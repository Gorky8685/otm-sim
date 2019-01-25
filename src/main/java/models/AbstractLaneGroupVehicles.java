package models;

import common.AbstractVehicle;
import common.Link;
import common.RoadConnection;
import error.OTMException;
import geometry.FlowDirection;
import geometry.Side;
import packet.AbstractPacketLaneGroup;
import packet.FluidLaneGroupPacket;
import packet.PartialVehicleMemory;
import packet.VehicleLaneGroupPacket;
import runner.RunParameters;
import runner.Scenario;

import java.util.Set;

public abstract class AbstractLaneGroupVehicles extends AbstractLaneGroup {

    protected PartialVehicleMemory partial_veh_mem;

    public AbstractLaneGroupVehicles(Link link, Side side, FlowDirection flwdir, float length, int num_lanes, int start_lane, Set<RoadConnection> out_rcs) {
        super(link, side, flwdir, length, num_lanes, start_lane, out_rcs);
    }

    @Override
    public void initialize(Scenario scenario, RunParameters runParams) throws OTMException {
        super.initialize(scenario, runParams);
        this.partial_veh_mem = new PartialVehicleMemory(scenario.commodities);
    }

    @Override
    public double get_supply() {
        return get_space();
    }

    @Override
    public float vehs_out_for_comm(Long comm_id) {
        System.err.println("NOT IMPLEMENTED");
        return Float.NaN;
    }

    protected Set<AbstractVehicle> create_vehicles_from_packet(AbstractPacketLaneGroup avp,Long next_link_id) {

        // + The packet received here can be fluid or vehicle based. It will not be both
        // because LaneGroupPackets are already model specific, in the sense of either
        // fluid or vehicle based
        // + All of the keys in the pacjet should be updated using next_link_id.

        Set<AbstractVehicle> vehicles = null;
        AbstractVehicleModel model = (AbstractVehicleModel) link.model;
        if (avp instanceof VehicleLaneGroupPacket) {
            for(AbstractVehicle abs_veh : ((VehicleLaneGroupPacket) avp).vehicles)
                vehicles.add(model.translate_vehicle(abs_veh));
            return vehicles;
        }

        if (avp instanceof FluidLaneGroupPacket) {
            return partial_veh_mem.process_fluid_packet((FluidLaneGroupPacket) avp);
        }
    }

    @Override
    public float get_current_travel_time() {
        System.err.println("NOT IMPLEMENTED");
        return Float.NaN;
    }

    @Override
    public void set_max_density_vpkpl(Float max_density_vpkpl) throws OTMException {
        throw new OTMException("NOT IMPLEMENTED");
    }

    @Override
    public void set_max_flow_vpspl(Float max_flow_vpspl) throws OTMException {
        throw new OTMException("NOT IMPLEMENTED");
    }


    @Override
    public void exiting_roadconnection_capacity_has_been_modified(float timestamp) {
        System.err.println("NOT IMPLEMENTED");
    }

    @Override
    public void set_max_speed_mps(Float max_speed_mps) throws OTMException {
        throw new OTMException("NOT IMPLEMENTED");
    }

    @Override
    public float vehs_dwn_for_comm(Long comm_id) {
        System.err.println("NOT IMPLEMENTED");
    }

    @Override
    public float vehs_in_for_comm(Long comm_id) {
        System.err.println("NOT IMPLEMENTED");
    }

}
