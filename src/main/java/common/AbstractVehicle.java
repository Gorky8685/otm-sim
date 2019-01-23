/**
 * Copyright (c) 2018, Gabriel Gomes
 * All rights reserved.
 * This source code is licensed under the standard 3-clause BSD license found
 * in the LICENSE file in the root directory of this source tree.
 */
package common;

import commodity.Commodity;
import keys.KeyCommPathOrLink;
import models.AbstractLaneGroup;
import output.InterfaceVehicleListener;
import utils.OTMUtils;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractVehicle {

    private long id;
    private Commodity comm;
    private KeyCommPathOrLink key;
    protected AbstractLaneGroup lg;

    // dispatch listeners
    private Set<InterfaceVehicleListener> event_listeners;

    public AbstractVehicle(){}

    public AbstractVehicle(AbstractVehicle that){
        this.id = that.getId();
        this.key = that.key;
        this.comm = that.comm;
        this.event_listeners = that.event_listeners;
    }

    public AbstractVehicle(Commodity comm){
        this.id = OTMUtils.get_vehicle_id();
        this.comm = comm;
        this.event_listeners = new HashSet<>();
        this.event_listeners.addAll(comm.vehicle_event_listeners);
        this.lg = null;
    }

    public void set_next_link_id(Long nextlink_id){
        assert(!comm.pathfull);
        key = new KeyCommPathOrLink(comm.getId(),nextlink_id,false);
    }

    ////////////////////////////////////////////////
    // getters
    ////////////////////////////////////////////////

    public long getId(){
        return id;
    }

    public long get_commodity_id(){
        return comm.getId();
    }

    public AbstractLaneGroup get_lanegroup(){
        return lg;
    }

    ////////////////////////////////////////////
    // event listeners
    ////////////////////////////////////////////

    public void add_event_listeners(Set<InterfaceVehicleListener> x){
        this.event_listeners.addAll(x);
    }

    public void remove_event_listeners(Set<InterfaceVehicleListener> x){
        this.event_listeners.removeAll(x);
    }

    public Set<InterfaceVehicleListener> get_event_listeners(){
        return event_listeners;
    }


    ////////////////////////////////////////////
    // key
    ////////////////////////////////////////////

    public void set_key(KeyCommPathOrLink key){
        assert(key.commodity_id==this.comm.getId());
        this.key = key;
    }

    public KeyCommPathOrLink get_key(){
        return key;
    }

    // NOTE: We do not update the next link id when it is null. This happens in
    // sinks. This means that the state in a sink needs to be interpreted
    // differently, which must be accounted for everywhere.
//    public void set_next_link_id(Long next_link_id){
//        if(!key.isPath && next_link_id!=null)
//            key = new KeyCommPathOrLink(key.commodity_id,next_link_id,false);
//    }


    @Override
    public String toString() {
        String str = "";
        str += "id " + id + "\n";
        str += "commodity_id " + comm.getId() + "\n";
        str += "in lanegroup " + (lg ==null?"none": lg.id) + "\n";
        return str;
    }

}
