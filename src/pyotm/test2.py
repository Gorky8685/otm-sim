from pyotm.OTMWrapper import OTMWrapper
import numpy as np
import pandas as pd
if "otm" in locals():
	del otm

otm = OTMWrapper("line_ctm.xml")
otm.run(start_time=0,duration=2500,output_dt=10)
Y = otm.get_state_trajectory()
print(Y.keys())
np.savez(r'C:\Users\MECHREVO\Desktop\Y.npz')
writer = pd.ExcelWriter('C:/Users/MECHREVO/Desktop/Y.xlsx')

# Convert each array in Y to a DataFrame and write to the Excel file
for key, array in Y.items():
    df = pd.DataFrame(array)
    df.to_excel(writer, sheet_name=key)

# Close the Pandas Excel writer and save the Excel file to disk
writer.save()
del otm