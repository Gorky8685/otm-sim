from pyotm.OTMWrapper import OTMWrapper
import numpy as np

if "otm" in locals():
	del otm

otm = OTMWrapper("line_ctm.xml")
otm.run(start_time=0,duration=2500,output_dt=10)
Y = otm.get_state_trajectory()
z=otm.lineplot()
print(Y.keys())
del otm