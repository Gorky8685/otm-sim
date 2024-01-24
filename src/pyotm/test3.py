from pyotm.OTMWrapper import OTMWrapper

# in case there is a lingering open gateway
if "otm" in locals():
    del otm

# load the configuration file
otm = OTMWrapper("intersection.xml")

# initialize (prepare/rewind the simulation)
otm.initialize(start_time=0.0)

scenario = otm.otm.scenario()
actuators = {id: scenario.get_actuator(id) for id in scenario.actuator_ids()}
commodities = {id: scenario.get_commodity(id) for id in scenario.commodity_ids()}
controllers = {id: scenario.get_controller(id) for id in scenario.controller_ids()}

network = scenario.network()
link_ids = list(network.link_ids())
links = {id: network.get_link(id) for id in link_ids}
nodes = {id: network.get_node(id) for id in network.node_ids()}
source_link_ids = network.source_link_ids()
link1 = links[1]

# run step-by-step using the 'advance' method
time = 0.0  # in seconds
advance_time = 10.0
while (time < 3600.0):
    otm.advance(advance_time)  # seconds, should be a multiple of sim_dt
    print(otm.get_current_time(), link1.get_veh())
    time += advance_time;

# deleting the wrapper to shut down the gateway
del otm
