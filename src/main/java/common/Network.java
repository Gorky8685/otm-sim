/**
 * Copyright (c) 2018, Gabriel Gomes
 * All rights reserved.
 * This source code is licensed under the standard 3-clause BSD license found
 * in the LICENSE file in the root directory of this source tree.
 */
package common;

import error.OTMErrorLog;
import error.OTMException;
import geometry.AddLanes;
import geometry.FlowDirection;
import geometry.RoadGeometry;
import geometry.Side;
import keys.KeyCommPathOrLink;
import models.ctm.NodeModel;
import models.ctm.UpLaneGroup;
import models.micro.LaneGroup;
import packet.PacketLink;
import runner.RunParameters;
import runner.Scenario;
import utils.OTMUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Network {

    private Long max_rcid;

    public Scenario scenario;
    public Map<Long,Node> nodes;
    public Map<Long,Link> links;
    public Map<Long, RoadGeometry> road_geoms = new HashMap<>();
    public Map<Long,jaxb.Roadparam> road_params = new HashMap<>();    // keep this for the sake of the scenario splitter

    public Map<Long,RoadConnection> road_connections = new HashMap<>();

    public Set<models.ctm.LinkModel> macro_link_models = new HashSet<>();
    public Set<Node> macro_internal_nodes = new HashSet<>();
    public Set<models.ctm.Source> macro_sources = new HashSet<>();

    ///////////////////////////////////////////
    // construction
    ///////////////////////////////////////////

    public Network(Scenario scneario){
        this.scenario = scneario;
        nodes = new HashMap<>();
        links = new HashMap<>();
    }

    public Network(Scenario scenario,List<jaxb.Node> jaxb_nodes, List<jaxb.Link> jaxb_links, jaxb.Model model, jaxb.Roadgeoms jaxb_geoms, jaxb.Roadconnections jaxb_conns, jaxb.Roadparams jaxb_params) throws OTMException {

        this(scenario);

        // read nodes
        for( jaxb.Node jn : jaxb_nodes ) {
            long id = jn.getId();
            if( nodes.containsKey(id) )
                throw new OTMException("Tried to add duplicate node id " + id);
            nodes.put(id,new Node(this,jn));
        }

        // read road geoms
        road_geoms = new HashMap<>();
        if(jaxb_geoms!=null)
            for(jaxb.Roadgeom jaxb_geom : jaxb_geoms.getRoadgeom())
                road_geoms.put(jaxb_geom.getId(),new RoadGeometry(jaxb_geom));

        // specified models
        Map<Long,Link.ModelType> specified_models = new HashMap<>();
        if(model!=null){

            // point-queue
            if (model.getPointQueue()!=null) {
                List<Long> ids = OTMUtils.csv2longlist(model.getPointQueue().getContent());
                ids.forEach(x -> specified_models.put(x, Link.ModelType.pq));
            }

            // ctm
            if (model.getCtm()!=null) {
                List<Long> ids = OTMUtils.csv2longlist(model.getCtm().getContent());
                ids.forEach(x -> specified_models.put(x, Link.ModelType.ctm));
            }

            // mn
            if (model.getMn()!=null) {
                List<Long> ids = OTMUtils.csv2longlist(model.getMn().getContent());
                ids.forEach(x -> specified_models.put(x, Link.ModelType.mn));
            }

        }

        // create links
        macro_link_models = new HashSet<>();
        for( jaxb.Link jl : jaxb_links ) {
            long id = jl.getId();

            // check if we have the link id
            if( links.containsKey(id)  )
                throw new OTMException("Tried to add duplicate link id " + id );

            // get its type. if not specified, it is models.ctm.pq
            Link.ModelType my_model_type = specified_models.containsKey(id) ? specified_models.get(id) : Link.ModelType.none;

            Link link = new Link(this,
                    my_model_type,
                    jl.getRoadparam() ,
                    jl.getRoadgeom()==null ? null : road_geoms.get(jl.getRoadgeom()),
                    jl.getRoadType()==null ? Link.RoadType.none : Link.RoadType.valueOf(jl.getRoadType()) ,
                    id,
                    jl.getLength(),
                    jl.getFullLanes(),
                    jl.getPoints(),
                    nodes.get(jl.getStartNodeId()),
                    nodes.get(jl.getEndNodeId()) );

            // set model
            switch(my_model_type) {

                case pq:
                    models.pq.LinkModel pq_model = new models.pq.LinkModel(link);
                    link.set_model(pq_model);
                    break;

                case ctm:
                    models.ctm.LinkModel ctm_model = new models.ctm.LinkModel(link);
                    link.set_model(ctm_model);
                    macro_link_models.add(ctm_model);
                    break;

                case mn:
                    models.ctm.LinkModel mn_model = new models.ctm.LinkModel(link);
                    link.set_model(mn_model);
                    macro_link_models.add(mn_model);
                    break;

                case none:
                    models.none.LinkModel none_model = new models.none.LinkModel(link);
                    link.set_model(none_model);
                    break;

            }
            links.put(id,link);

        }

        // nodes is_many2one
        nodes.values().stream().forEach(node -> node.is_many2one = node.out_links.size()==1);

        // abort if we have macro links but were not given a sim_dt
        if( !macro_link_models.isEmpty() && Float.isNaN(scenario.sim_dt) )
            throw new OTMException("Attempted to load a scenario with macroscopic links, but did not provide a simulation time step.");

        // read road connections
        road_connections = new HashMap<>();
        if(jaxb_conns!=null && jaxb_conns.getRoadconnection()!=null)
            for(jaxb.Roadconnection jaxb_rc : jaxb_conns.getRoadconnection() )
                road_connections.put(jaxb_rc.getId(),new RoadConnection(this.links,jaxb_rc));

        max_rcid = road_connections.isEmpty() ? 0L : road_connections.keySet().stream().max(Long::compareTo).get();

        // create lane groups .......................................
        for(Link link : links.values()) {   // not parallelizable

            // set sources and sinks according to incoming and outgoing links
            link.is_source = link.start_node.in_links.isEmpty();
            link.is_sink = link.end_node.out_links.isEmpty();

            // get road connections that exit this link
            Set<RoadConnection> out_rcs = road_connections.values().stream().filter(x->x.start_link.id==link.id).collect(toSet());

            // empty out_rc is allowed only if num_out<=1
            if(out_rcs.isEmpty() && link.end_node.out_links.size()>1)
                throw new OTMException("No road connection leaving link " + link.getId() + ", although it is neither a sink nor a single next link case.");

            // absent road connections: create them, if it is not a sink
            if(out_rcs.isEmpty() && !link.is_sink) { // sink or single next link
                Map<Long,RoadConnection> new_rcs = create_missing_road_connections(link);
                out_rcs.addAll(new_rcs.values());
                road_connections.putAll(new_rcs);
            }

            // create lanegroups
            link.set_long_lanegroups(create_dnflw_lanegroups(link,out_rcs));
            create_up_side_lanegroups(link);

            // set start_lane_up
            int up_in_lanes = link.road_geom!=null && link.road_geom.up_in!=null ? link.road_geom.up_in.lanes : 0;
            int dn_in_lanes = link.road_geom!=null && link.road_geom.dn_in!=null ? link.road_geom.dn_in.lanes : 0;
            int offset = dn_in_lanes-up_in_lanes;
            for(AbstractLaneGroup lg : link.lanegroups_flwdn.values())
                if(lg.side==Side.full)
                    lg.start_lane_up = lg.start_lane_dn - offset;

            // set neighbors

            // .................. lat lanegroups = {up addlane}
            if(link.lanegroup_flwside_in !=null){
                AbstractLaneGroup inner_full = link.get_inner_full_lanegroup();
                link.lanegroup_flwside_in.neighbor_out = inner_full;
                inner_full.neighbor_up_in = link.lanegroup_flwside_in;
            }

            if (link.lanegroup_flwside_out != null) {
                AbstractLaneGroup outer_full = link.get_outer_full_lanegroup();
                link.lanegroup_flwside_out.neighbor_in = outer_full;
                outer_full.neighbor_up_out = link.lanegroup_flwside_out;
            }

            // ................... long lanegroups = {dn addlane, full lgs}
            int num_dn_lanes = link.get_num_dn_lanes();
            if(num_dn_lanes>1) {
                List<AbstractLaneGroup> long_lgs = IntStream.rangeClosed(1, link.get_num_dn_lanes())
                        .mapToObj(lane -> link.dnlane2lanegroup.get(lane)).collect(toList());
                AbstractLaneGroup prev_lg = null;
                for (int lane = 1; lane <= num_dn_lanes; lane++) {

                    AbstractLaneGroup lg = long_lgs.get(lane - 1);
                    if (prev_lg == null)
                        prev_lg = lg;
                    if (lg != prev_lg) {
                        lg.neighbor_in = prev_lg;
                        prev_lg.neighbor_out = lg;
                        prev_lg = lg;
                    }
                }

                prev_lg = null;
                for(int lane=num_dn_lanes;lane>=1;lane--){
                    AbstractLaneGroup lg = long_lgs.get(lane-1);
                    if(prev_lg==null)
                        prev_lg = lg;
                    if(lg!=prev_lg) {
                        lg.neighbor_out = prev_lg;
                        prev_lg.neighbor_in = lg;
                        prev_lg = lg;
                    }
                }
            }
        }

        // set in/out lanegroups on road connections
        road_connections.values().forEach(x->x.set_in_out_lanegroups());

        // store list of road connections in nodes
        for(common.RoadConnection rc : road_connections.values()) {
            if (rc.start_link.end_node != rc.end_link.start_node) {
                System.err.println("bad road connection: id=" + rc.getId()
                        + " start_link = " + rc.start_link.getId()
                        + " end_link = " + rc.end_link.getId()
                        + " start_link.end_node = " + rc.start_link.end_node.getId()
                        + " end_link.start_node = " + rc.end_link.start_node.getId() );
            }

            rc.start_link.end_node.add_road_connection(rc);
        }

        // populate link.outlink2lanegroups
        for(Link link : links.values()){

            // case sink
            if(link.is_sink)
                continue;

            // for each outlink, add all lanegroups from which outlink is reachable
            link.outlink2lanegroups = new HashMap<>();
            for(Long outlink_id : link.end_node.out_links.keySet()) {
                // lane groups that connect to outlink_id
                Set<AbstractLaneGroup> connected_lgs = link.lanegroups_flwdn.values().stream()
                                                           .filter(lg -> lg.link_is_link_reachable(outlink_id)).collect(toSet());
                if(!connected_lgs.isEmpty())
                    link.outlink2lanegroups.put(outlink_id,connected_lgs);
            }
        }

        // construct cells for macro links (this has to be after sources are set)
        if(!macro_link_models.isEmpty()){

            // populate macro_internal_nodes: connected in any way to ctm models, minus sources and sinks
            Set<Node> all_nodes = macro_link_models.stream().map(x->x.link.start_node).collect(toSet());
            all_nodes.addAll(macro_link_models.stream().map(x->x.link.end_node).collect(toSet()));
            all_nodes.removeAll(nodes.values().stream().filter(node->node.is_source || node.is_sink).collect(Collectors.toSet()));
            macro_internal_nodes = all_nodes;

            // give them models.ctm node models
            for(Node node : macro_internal_nodes)
                node.set_macro_model( new NodeModel(node) );

            // ctm links
            if(model.getCtm()!=null)
                links.values().stream()
                        .filter(x->x.model_type==Link.ModelType.ctm)
                        .forEach(v->((models.ctm.LinkModel)v.model).create_cells(model.getCtm().getMaxCellLength()));

            // mn links
            if(model.getMn()!=null)
                links.values().stream()
                        .filter(x->x.model_type==Link.ModelType.mn)
                        .forEach(v->((models.ctm.LinkModel)v.model).create_cells(model.getMn().getMaxCellLength()));

        }

        // assign road params
        road_params = new HashMap<>();
        if(jaxb_params!=null)
            for(jaxb.Roadparam r : jaxb_params.getRoadparam())
                road_params.put(r.getId(),r);

        for( jaxb.Link jl : jaxb_links ) {
            Link link = links.get(jl.getId());
            jaxb.Roadparam rp = road_params.get(jl.getRoadparam());
            if(rp==null)
                throw new OTMException("No road parameters for link id " + jl.getId()  );
            link.model.set_road_param(rp,scenario.sim_dt);
        }

    }

    // constuctor for static scenario
    public Network(Scenario scenario,List<jaxb.Node> jaxb_nodes, List<jaxb.Link> jaxb_links, jaxb.Roadparams jaxb_params) throws OTMException {

        this(scenario);

        // read nodes
        for( jaxb.Node jn : jaxb_nodes ) {
            long id = jn.getId();
            if( nodes.containsKey(id) )
                throw new OTMException("Tried to add duplicate node id " + id);
            nodes.put(id,new Node(this,jn));
        }

        // create links
        macro_link_models = new HashSet<>();
        for( jaxb.Link jl : jaxb_links ) {
            long id = jl.getId();

            // check if we have the link id
            if( links.containsKey(id)  )
                throw new OTMException("Tried to add duplicate link id " + id );

            // get its type. if not specified, it is models.ctm.pq
            Link link = new Link(this,
                    null,
                    jl.getRoadparam() ,
                    null,
                    null ,
                    id,
                    jl.getLength(),
                    jl.getFullLanes(),
                    null,
                    nodes.get(jl.getStartNodeId()),
                    nodes.get(jl.getEndNodeId()) );

            models.ctm.LinkModel none_model = new models.ctm.LinkModel(link);
            link.set_model(none_model);
            links.put(id,link);
        }

        // assign road params
        road_params = new HashMap<>();
        if(jaxb_params!=null)
            for(jaxb.Roadparam r : jaxb_params.getRoadparam())
                road_params.put(r.getId(),r);

        for( jaxb.Link jl : jaxb_links ) {
            Link link = links.get(jl.getId());
            jaxb.Roadparam rp = road_params.get(jl.getRoadparam());
            if(rp==null)
                throw new OTMException("No road parameters for link id " + jl.getId()  );
            link.model.set_road_param(rp,scenario.sim_dt);
        }

    }

    public void validate(Scenario scenario,OTMErrorLog errorLog){
        nodes.values().forEach(x->x.validate(scenario,errorLog));
        links.values().forEach(x->x.validate(errorLog));
        road_geoms.values().forEach(x->x.validate(errorLog));
        road_connections.values().forEach(x->x.validate(errorLog));
    }

    public void initialize(Scenario scenario,RunParameters runParams) throws OTMException {

        for(Link link : links.values())
            link.initialize(scenario,runParams);

        for(Node node: nodes.values())
            node.initialize(scenario,runParams);
    }

    // ***********************************************************
    // TODO : Make these static and put them into scenarioFactory

    private Map<Long,RoadConnection> create_missing_road_connections(Link link){

        Map<Long,RoadConnection> new_rcs = new HashMap<>();
        int start_lane = 1;
        int lanes;
        Long rc_id;
        RoadConnection newrc;
        Link end_link = link.end_node.out_links.values().iterator().next();
        int end_link_lanes = end_link.get_num_up_lanes();

        // dn in rc
        if(link.road_geom!=null && link.road_geom.dn_in!=null){
            rc_id = ++max_rcid;
            lanes = link.road_geom.dn_in.lanes;
            newrc = new RoadConnection(rc_id,link,start_lane,start_lane+lanes-1,end_link,1,end_link_lanes);
            new_rcs.put(rc_id, newrc);
            start_lane += lanes;
        }

        // full rc
        rc_id = ++max_rcid;
        lanes = link.full_lanes;
        newrc = new RoadConnection(rc_id,link,start_lane,start_lane+lanes-1,end_link,1,end_link_lanes);
        new_rcs.put(rc_id, newrc);
        start_lane += lanes;

        // dn out rc
        if(link.road_geom!=null && link.road_geom.dn_out!=null){
            rc_id = ++max_rcid;
            lanes = link.road_geom.dn_out.lanes;
            newrc = new RoadConnection(rc_id,link,start_lane,start_lane+lanes-1,end_link,1,end_link_lanes);
            new_rcs.put(rc_id, newrc);
        }
        return new_rcs;
    }

    private Set<AbstractLaneGroup> create_dnflw_lanegroups(Link link, Set<RoadConnection> out_rcs) throws OTMException {
        // Find unique subsets of road connections, and create a lane group for each one.

        Set<AbstractLaneGroup> lanegroups = new HashSet<>();

        // empty out_rc => sink
        if(out_rcs.isEmpty()){
            assert(link.is_sink);
            lanegroups.add(create_dnflw_lanegroup(link,1, link.full_lanes, null));
            return lanegroups;
        }

        // faster code for singleton
        if(out_rcs.size()==1) {
            lanegroups.add(create_dnflw_lanegroup(link, 1, link.full_lanes, out_rcs));
            return lanegroups;
        }

        // create map from lanes to road connection sets
        Map<Integer,Set<RoadConnection>> dnlane2rcs = new HashMap<>();
        for(int lane=1;lane<=link.get_num_dn_lanes();lane++) {
            Set<RoadConnection> myrcs = new HashSet<>();
            for (RoadConnection rc : out_rcs){
                if (rc.start_link_from_lane <= lane && rc.start_link_to_lane >= lane)
                    myrcs.add(rc);
            }
            if(myrcs.isEmpty())
                throw new OTMException(String.format("Lane %d in link %d has no outgoing road connection",lane,link.getId()));

            dnlane2rcs.put(lane,myrcs);
        }

        // set of unique road connection sets
        Set<Set<RoadConnection>> unique_rc_sets = new HashSet<>();
        unique_rc_sets.addAll(dnlane2rcs.values());

        // create a lane group for each unique_rc_sets
        for(Set<RoadConnection> my_rcs : unique_rc_sets) {
            Set<Integer> lg_lanes = dnlane2rcs.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(my_rcs))
                    .map(entry->entry.getKey())
                    .collect(Collectors.toSet());
            int dn_start_lane = lg_lanes.stream().mapToInt(x->x).min().getAsInt();
            int num_lanes = lg_lanes.size();
            lanegroups.add(create_dnflw_lanegroup(link, dn_start_lane, num_lanes, my_rcs));
        }

        return lanegroups;
    }

    private AbstractLaneGroup create_dnflw_lanegroup(Link link, int dn_start_lane, int num_lanes, Set<RoadConnection> out_rcs) throws OTMException {

        // Determine whether it is an addlane lanegroup or a full lane group.
        Set<Side> sides = new HashSet<>();
        for(int lane=dn_start_lane;lane<dn_start_lane+num_lanes;lane++)
            sides.add(link.get_side_for_dn_lane(lane));

        if(sides.size()!=1)
            throw new OTMException(String.format("Rule broken: Lane groups must be contained in addlanes or full lanes. Check link %d",link.getId()));

        float length = 0f;
        Side side = sides.iterator().next();
        switch(side){
            case in:
                length = link.road_geom.dn_in.length;
                break;
            case full:
                length = link.length;
                break;
            case out:
                length = link.road_geom.dn_out.length;
                break;
        }

        AbstractLaneGroup lg = null;
        switch(link.model_type){
            case ctm:
            case mn:
                lg = new models.ctm.LaneGroup(link,side, FlowDirection.dn,length,num_lanes,dn_start_lane,out_rcs);
                break;
            case pq:
                lg = new models.pq.LaneGroup(link,side, FlowDirection.dn,length,num_lanes,dn_start_lane,out_rcs);
                break;
            case micro:
                lg = new LaneGroup(link,side, FlowDirection.dn,length,num_lanes,dn_start_lane,out_rcs);
                break;
            case none:
                lg = new models.none.LaneGroup(link,side, FlowDirection.dn,length,num_lanes,dn_start_lane,out_rcs);
                break;
        }
        return lg;
    }

    private void create_up_side_lanegroups(Link link) throws OTMException {
        if(link.road_geom==null)
            return;
        if(link.road_geom.up_in!=null)
            link.lanegroup_flwside_in = create_up_side_lanegroup(link, link.road_geom.up_in);
        if(link.road_geom.up_out!=null)
            link.lanegroup_flwside_out = create_up_side_lanegroup(link,link.road_geom.up_out);
    }

    private AbstractLaneGroup create_up_side_lanegroup(Link link, AddLanes addlanes) {
        float length = addlanes.length;
        int num_lanes = addlanes.lanes;
        Side side = addlanes.side;
        int start_lane_up = side==Side.in ? 1 : link.get_num_up_lanes() - addlanes.lanes + 1;

        AbstractLaneGroup lg = null;
        switch(link.model_type){
            case ctm:
            case mn:
                lg = new models.ctm.LaneGroup(link,side, FlowDirection.up,length,num_lanes,start_lane_up,null);
                break;
            case pq:
                lg = new models.pq.LaneGroup(link,side, FlowDirection.up,length,num_lanes,start_lane_up,null);
                break;
            case micro:
                lg = new models.micro.LaneGroup(link,side, FlowDirection.up,length,num_lanes,start_lane_up,null);
                break;
            case none:
                lg = new models.none.LaneGroup(link,side, FlowDirection.up,length,num_lanes,start_lane_up,null);
                break;
        }
        return lg;
    }

    ///////////////////////////////////////////
    // update
    ///////////////////////////////////////////

    public void update_macro_flow(float timestamp) throws OTMException {

        update_macro_flow_part_I(timestamp);

        // -- MPI communication (in otm-mpi) -- //

        update_macro_flow_part_II(timestamp);
    }

    public void update_macro_state(float timestamp) {
        for(models.ctm.LinkModel linkModel : macro_link_models)
            linkModel.update_state(timestamp);
    }

    public void update_macro_flow_part_I(float timestamp){

        // lane changing -> intermediate state
        macro_link_models.stream()
                .filter(l -> l.link.lanegroups_flwdn.size()>=2)
                .forEach(l -> l.perform_lane_changes(timestamp));

        // update demand and supply
        // (cell.veh_dwn,cell.veh_out -> cell.demand_dwn , cell.demand_out)
        // (cell.veh_dwn,cell.veh_out -> cell.supply)
        macro_link_models.forEach(l -> l.update_supply_demand());

        // compute node inflow and outflow (all nodes except sources)
        macro_internal_nodes.forEach(node->node.node_model.update_flow(timestamp));

    }

    public void update_macro_flow_part_II(float timestamp) throws OTMException {

        // exchange packets
        for(Node node : macro_internal_nodes) {

            // flows on road connections arrive to links on give lanes
            // convert to packets and send
            for(models.ctm.RoadConnection rc : node.node_model.rcs.values())
                rc.rc.end_link.model.add_vehicle_packet(timestamp,new PacketLink(rc.f_rs,rc.rc.out_lanegroups));

            // set exit flows on non-sink lanegroups
            for(UpLaneGroup ulg : node.node_model.ulgs.values())
                ulg.lg.release_vehicles(ulg.f_is);
        }

        // update cell boundary flows
        macro_link_models.forEach(l -> l.update_dwn_flow());

    }

    ////////////////////////////////////////////
    // get / set
    ///////////////////////////////////////////

    public Set<AbstractLaneGroup> get_lanegroups(){
        return links.values().stream().flatMap(link->link.lanegroups_flwdn.values().stream()).collect(toSet());
    }

    public Collection<RoadConnection> get_road_connections(){
        return road_connections.values();
    }

    public RoadConnection get_road_connection(Long id){
        return road_connections.get(id);
    }

    @Override
    public String toString() {
        return String.format("%d nodes, %d links",nodes.size(),links.size());
    }

    public jaxb.Network to_jaxb(){
        jaxb.Network jnet = new jaxb.Network();


        // network: nodes
        jaxb.Nodes jnodes = new jaxb.Nodes();
        jnet.setNodes(jnodes);
        for(Node node : nodes.values())
            jnodes.getNode().add(node.to_jaxb());

        // network: links
        jaxb.Links jlinks = new jaxb.Links();
        jnet.setLinks(jlinks);
        for(Link link : links.values())
            jlinks.getLink().add(link.to_jaxb());

        // network: roadgeoms
        jaxb.Roadgeoms jgeoms = new jaxb.Roadgeoms();
        jnet.setRoadgeoms(jgeoms);
        for(geometry.RoadGeometry geom : road_geoms.values())
            jgeoms.getRoadgeom().add(geom.to_jaxb());

        // network: roadconnections
        jaxb.Roadconnections jconns = new jaxb.Roadconnections();
        jnet.setRoadconnections(jconns);
        for(common.RoadConnection rcn : road_connections.values())
            jconns.getRoadconnection().add(rcn.to_jaxb());

        // network: roadparams
        jaxb.Roadparams jrpms = new jaxb.Roadparams();
        jnet.setRoadparams(jrpms);
        jrpms.getRoadparam().addAll(road_params.values());

        return jnet;
    }

}
