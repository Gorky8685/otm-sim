package actuator;

import core.*;
import control.AbstractController;
import control.command.InterfaceCommand;
import dispatch.Dispatcher;
import dispatch.EventPoke;
import dispatch.Pokable;
import error.OTMErrorLog;
import error.OTMException;
import utils.OTMUtils;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractActuator implements Pokable, InterfaceScenarioElement {

    public enum Type {
        flowtolink,
        lg_allowcomm,
        lg_capacity,
        lg_speedlimit,
        signal,
        split,
    }

    public long id;
    public abstract Type getType();
    public Float dt;            // dt<=0 means event based (vehicle model) or dt=sim dt (fluid model)
    public boolean initialized;

    public AbstractController myController;
    public InterfaceTarget target;
    public Set<Long> commids; // not always used

    abstract public void process_controller_command(InterfaceCommand command, float timestamp) throws OTMException;
    abstract protected ScenarioElementType get_target_class();

    ///////////////////////////////////////////
    // construction
    ///////////////////////////////////////////

    public AbstractActuator(Scenario scenario, jaxb.Actuator jaxb_actuator) throws OTMException {
        this.id = jaxb_actuator.getId();
        this.dt = jaxb_actuator.getDt();
        this.initialized = false;
        if(jaxb_actuator.getActuatorTarget()!=null){
            jaxb.ActuatorTarget e = jaxb_actuator.getActuatorTarget();
            Long id = e.getId()==null ? null : Long.parseLong(e.getId());

            ScenarioElementType type ;
            try {
                // this will throw an exception if the type is not a ScenarioElementType
                type = ScenarioElementType.valueOf(e.getType());

                if(type!=get_target_class())
                    throw new OTMException("Wrong target type in actuator");

                if(type!=null && type!=ScenarioElementType.lanegroups)
                    this.target = (InterfaceTarget) scenario.get_element(type,id);

                if(e.getCommids()!=null) {
                    this.commids = new HashSet<>();
                    commids.addAll(OTMUtils.csv2longlist(e.getCommids()));
                }

            } catch (IllegalArgumentException illegalArgumentException) {
                // if exception is thrown, set target to null.
                // and resolve in higher level constructor
                this.target = null;
            }
        }
    }

    ///////////////////////////////////////////
    // InterfaceScenarioElement
    ///////////////////////////////////////////

    @Override
    public final Long getId() {
        return id;
    }

    @Override
    public final ScenarioElementType getSEType() {
        return ScenarioElementType.actuator;
    }

    public void validate_pre_init(OTMErrorLog errorLog) {
    }

    public void initialize(Scenario scenario, float timestamp, boolean override_targets) throws OTMException {

        if(initialized)
            return;

        // assign dt according to target model if dt==null
        set_dt_for_target();

        if(dt!=null)
            scenario.dispatcher.register_event(new EventPoke(scenario.dispatcher,3,timestamp,this));

        initialized=true;
    }

    protected void set_dt_for_target(){
        if(target!=null && (dt==null || dt<=0)){
            AbstractModel model = this.target.get_model();
            if(model==null)
                dt=null;
            else {
                if (model instanceof AbstractFluidModel)
                    dt = ((AbstractFluidModel) model).dt_sec;
                if (model instanceof AbstractVehicleModel)
                    dt = null;
            }
        }
    }

    public void validate_post_init(OTMErrorLog errorLog){
        if(target==null)
            errorLog.addWarning("Actuator has no target");
    }

    @Override
    public OTMErrorLog to_jaxb() {
        return null;
    }

    /////////////////////////////////////////////////////////////////////
    // Pokable
    /////////////////////////////////////////////////////////////////////

    @Override
    public void poke(Dispatcher dispatcher, float timestamp) throws OTMException {

        // process the command
        if(myController!=null)
            process_controller_command(myController.get_command_for_actuator_id(id),timestamp);

        // wake up in dt, if dt is defined
        if(dt!=null)
            dispatcher.register_event(new EventPoke(dispatcher,3,timestamp+dt,this));
    }

}
