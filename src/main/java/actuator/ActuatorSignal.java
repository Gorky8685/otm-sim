package actuator;

import control.command.CommandSignal;
import control.command.InterfaceCommand;
import error.OTMErrorLog;
import error.OTMException;
import core.Scenario;

import java.util.HashMap;
import java.util.Map;

public class ActuatorSignal extends AbstractActuatorLanegroupCapacity {

    public Map<Long, SignalPhase> signal_phases;

    ///////////////////////////////////////////////////
    // construction
    ///////////////////////////////////////////////////

    public ActuatorSignal(Scenario scenario, jaxb.Actuator jaxb_actuator) throws OTMException {
        super(scenario,jaxb_actuator);

        // must be on a node
        if(target==null || !(target instanceof core.Node))
            return;

        if(jaxb_actuator.getSignal()==null)
            return;

        signal_phases = new HashMap<>();
        for(jaxb.Phase jaxb_phase : jaxb_actuator.getSignal().getPhase())
            signal_phases.put(jaxb_phase.getId(), new SignalPhase(scenario, this, jaxb_phase));

    }

    ///////////////////////////////////////////////////
    // InterfaceScenarioElement
    ///////////////////////////////////////////////////

    @Override
    public void validate(OTMErrorLog errorLog) {
        super.validate(errorLog);

        if(signal_phases==null)
            errorLog.addError("ActuatorSignal ID=" + id + " contains no valid phases.");
        else
            for(SignalPhase p : signal_phases.values())
                p.validate(errorLog);
    }

    @Override
    public void initialize(Scenario scenario, float start_time) throws OTMException {

        if(initialized)
            return;

        super.initialize(scenario,start_time);

        // set all bulb colors to dark
        for(SignalPhase p : signal_phases.values() )
            p.initialize();
    }

    ///////////////////////////////////////////////////
    // AbstractActuator
    ///////////////////////////////////////////////////

    @Override
    public Type getType() {
        return Type.signal;
    }

    @Override
    public void process_controller_command(InterfaceCommand obj, float timestamp) throws OTMException {
        // The command is a map from signal phase to color.
        // anything not in the map should be set to red
        Map<Long, SignalPhase.BulbColor> command = ((CommandSignal)obj).value;
        for( Map.Entry<Long, SignalPhase> e : signal_phases.entrySet()){
            long phase_id = e.getKey();
            SignalPhase phase = e.getValue();
            SignalPhase.BulbColor bulbcolor = command.containsKey(phase_id) ? command.get(phase_id) : SignalPhase.BulbColor.RED;
            phase.set_bulb_color(bulbcolor);
        }
    }

    ///////////////////////////////////////////////////
    // get
    ///////////////////////////////////////////////////

    public SignalPhase get_phase(long phase_id){
        return signal_phases.get(phase_id);
    }

}
