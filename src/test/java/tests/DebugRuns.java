package tests;

import error.OTMException;
import models.vehicle.spatialq.OutputQueues;
import org.junit.Ignore;
import org.junit.Test;
import output.*;
import cmd.OTM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class DebugRuns extends AbstractTest {

    @Ignore
    @Test
    public void run_one() {
        try {

            // ..........................................

            boolean do_links        = true;
            boolean do_lanegroups   = true;
            boolean do_cells        = true;
            boolean do_subnetworks  = false;
            boolean do_vehicles     = false;
            boolean do_controllers  = false;

            boolean sysout2file = false;
            String configfile = "/home/gomes/Downloads/aaa_0.xml";
            float start_time = 0f;
            float duration = 86400f;
            float outdt = 300f;
            String prefix = "x";
            String output_folder = "/home/gomes/Downloads";

            Set<Long> link_ids =  Set.of(1l,2l,3l);

            Long subnetid = null;
            Long cntrl_id = null;

            Long comm_id=null;
            // ..........................................

            // redirect System.out to a file
            if(sysout2file) {
                try {
                    File file = new File(String.format("%s/log.txt", output_folder));
                    PrintStream stream = null;
                    stream = new PrintStream(file);
                    System.out.println("System.out directed to " + file.getAbsolutePath());
                    System.setOut(stream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            // Load ..............................
            api.OTM otm = new api.OTM(configfile,true,false);

            // links
            if(do_links){
                otm.output.request_links_flow(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_links_veh(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_links_sum_veh(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_link_queues(prefix,output_folder,comm_id,link_ids,outdt);
            }

            // lane groups
            if(do_lanegroups){
                otm.output.request_lanegroups(prefix,output_folder);
                otm.output.request_lanegroup_flw(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_lanegroup_veh(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_lanegroup_sum_veh(prefix,output_folder,comm_id,link_ids,outdt);
            }

            // cells
            if(do_cells){
                otm.output.request_cell_flw(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_cell_veh(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_cell_sum_veh(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_cell_sum_veh_dwn(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_cell_lanechange_out(prefix,output_folder,comm_id,link_ids,outdt);
                otm.output.request_cell_lanechange_in(prefix,output_folder,comm_id,link_ids,outdt);
            }

            // subnetworks
            if(do_subnetworks){
                otm.output.request_path_travel_time(prefix,output_folder,subnetid,outdt);
                otm.output.request_subnetwork_vht(prefix,output_folder,comm_id,subnetid,outdt);
            }

            // vehicle events
            if(do_vehicles){
                otm.output.request_vehicle_events(prefix,output_folder,comm_id);
                otm.output.request_vehicle_class(prefix,output_folder);
                otm.output.request_vehicle_travel_time(prefix,output_folder);
            }

            // controllers
            if(do_controllers){
                otm.output.request_controller(prefix,output_folder,cntrl_id);
            }

            // Run .................................
            otm.run(start_time,duration);

            // Print output .........................
            for(AbstractOutput output :  otm.output.get_data()){

                // links
                if (output instanceof OutputLinkFlow)
                    ((OutputLinkFlow) output).plot_for_links(null, String.format("%s/link_flow.png", output_folder));

                if (output instanceof OutputLinkVehicles)
                    ((OutputLinkVehicles) output).plot_for_links(null, String.format("%s/link_veh.png", output_folder));

                if (output instanceof OutputLinkSumVehicles)
                    ((OutputLinkSumVehicles) output).plot_for_links(null, String.format("%s/link_sumveh.png", output_folder));

                if (output instanceof OutputQueues)
                    ((OutputQueues) output).plot(String.format("%s/link_sumqueues.png", output_folder));

                // lane groups

                if (output instanceof OutputLaneGroupFlow) {
                    OutputLaneGroupFlow x = (OutputLaneGroupFlow) output;
                    String commid = x.commodity==null ? "all" : String.format("%d",x.commodity.getId());
                    String title = "Commodity " + (x.commodity==null ? "all" : x.commodity.name);
                    x.plot_for_links(null,title,  String.format("%s/lg_flow_%s.png", output_folder, commid));
                }

                if (output instanceof OutputLaneGroupVehicles) {
                    String title = "";
                    ((OutputLaneGroupVehicles) output).plot_for_links(null, title,String.format("%s/lg_veh.png", output_folder));
                }

                if (output instanceof OutputLaneGroupSumVehicles) {
                    String title = "";
                    ((OutputLaneGroupSumVehicles) output).plot_for_links(null, title,String.format("%s/lg_sumveh.png", output_folder));
                }

                // cells

                if (output instanceof OutputCellFlow)
                    ((OutputCellFlow) output).plot_for_links(null, String.format("%s/cell_flow.png", output_folder));

                if (output instanceof OutputCellVehicles)
                    ((OutputCellVehicles) output).plot_for_links(null, String.format("%s/cell_veh.png", output_folder));

                if (output instanceof OutputCellSumVehicles)
                    ((OutputCellSumVehicles) output).plot_for_links(null, String.format("%s/cell_sumveh.png", output_folder));

                if (output instanceof OutputCellSumVehiclesDwn)
                    ((OutputCellSumVehiclesDwn) output).plot_for_links(null, String.format("%s/cell_sumvehdwn.png", output_folder));

                if (output instanceof OutputCellLanechangeOut)
                    ((OutputCellLanechangeOut) output).plot_for_links(null, String.format("%s/cell_lc_out.png", output_folder));

                if (output instanceof OutputCellLanechangeIn)
                    ((OutputCellLanechangeIn) output).plot_for_links(null, String.format("%s/cell_lc_in.png", output_folder));

                // subnetworks
                if (output instanceof OutputPathTravelTime)
                    ((OutputPathTravelTime) output).plot(String.format("%s/path_tt.png", output_folder));

                if (output instanceof OutputLinkVHT)
                    ((OutputLinkVHT) output).plot_for_links(null, String.format("%s/vht.png", output_folder));

                // vehicle events

                if (output instanceof OutputVehicle)
                    ((OutputVehicle) output).plot(String.format("%s/veh_events.png", output_folder));

//                if (output instanceof OutputVehicleClass)
//                    ((OutputVehicleClass) output).plot(String.format("%s/veh_class.png", output_folder));

                if (output instanceof OutputTravelTime)
                    ((OutputTravelTime) output).plot(String.format("%s/veh_traveltime.png", output_folder));

                // controllers

                if (output instanceof OutputController)
                    ((OutputController) output).plot(String.format("%s/controller.png", output_folder));

            }

        } catch (OTMException e) {
            System.out.print(e);
            fail();
        }

    }

    @Ignore
    @Test
    public void run_step_by_step(){

        try {

            String configfile = "/home/gomes/Downloads/aaa_0.xml";

            float start_time = 21600f;
            float duration = 1000f;
            float outdt = 300f;
            float simdt = 5f;
            String prefix = "x";
            String output_folder = "/home/gomes/Downloads";

            // Load ..............................
            api.OTM otm = new api.OTM(configfile,true,false);

            // Output requests .....................
            Set<Long> link_ids = new HashSet<>();   //otm.scenario.get_link_ids();
            link_ids.add(3l);

//            otm.output.request_lanegroup_flw(0l,link_ids,outdt);
            otm.output.request_lanegroup_veh(null,null,0l,link_ids,outdt);

            // Run .................................
            otm.initialize(start_time);
            otm.advance(start_time);

            int simsteps = (int) Math.ceil(duration/simdt);
            int steps_taken = 0;
            while(steps_taken<simsteps){
                otm.advance(simdt);
                steps_taken += 1;
            }

            otm.terminate();

            // Print output .........................
            for(AbstractOutput output :  otm.output.get_data()){

                if (output instanceof OutputLaneGroupFlow) {
                    OutputLaneGroupFlow x = (OutputLaneGroupFlow) output;
                    String commid = x.commodity==null ? "all" : String.format("%d",x.commodity.getId());
                    String title = "Commodity " + (x.commodity==null ? "all" : x.commodity.name);
                    x.plot_for_links(null,title,  String.format("%s/lg_flow_%s.png", output_folder, commid));
                }

                if (output instanceof OutputLaneGroupVehicles) {
                    String title = "";
                    ((OutputLaneGroupVehicles) output).plot_for_links(null, title,String.format("%s/lg_veh.png", output_folder));
                }

            }

        } catch (OTMException e) {
            System.out.print(e);
            fail();
        }

    }

    @Ignore
    @Test
    public void load_save(){
        try {
            String configfile = "/home/gomes/Desktop/nrel_presentation/lakewood.xml";
            api.OTM otm = new api.OTM(configfile,false,false);
            otm.save("/home/gomes/Desktop/nrel_presentation/lakewood_savesd.xml");
            assertNotNull(otm);
        } catch (OTMException e) {
            fail(e.getMessage());
        }

    }

    @Ignore
    @Test
    public void load_with_plugin() {
        try {
            String configfile = "/home/gomes/code/otm-models/cfg/line.xml";
            api.OTM otm = new api.OTM(configfile,true,false);
            assertNotNull(otm);
        } catch (OTMException e) {
            fail(e.getMessage());
        }
    }

}