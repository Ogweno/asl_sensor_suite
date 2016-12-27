package asl.sensor;

import java.io.IOException;

import org.jfree.data.xy.XYSeries;

/**
 * Holds the inputted data from miniSEED files both as a simple struct
 * (see DataBlock) and as a plottable format for the DataPanel.
 * Also serves as container for loaded-in instrument response files
 * @author akearns
 *
 */
public class DataStore {

  /**
   * Defines the maximum number of plots to be shown
   */
  final static int FILE_COUNT = 3;
  DataBlock[] dataBlockArray;
  InstrumentResponse[] responses;
  XYSeries[] outToPlots;
  
  long startTime = 0L;
  long endTime = Long.MAX_VALUE;
  
  boolean[] dataIsSet;
  boolean[] responseIsSet;
  
  /**
   * Instantiate the collections, including empty datasets to be sent to
   * charts for plotting (see DataPanel)
   */
  public DataStore(){
   dataBlockArray = new DataBlock[FILE_COUNT];
   responses = new InstrumentResponse[FILE_COUNT];
   outToPlots = new XYSeries[FILE_COUNT];
   dataIsSet = new boolean[FILE_COUNT];
   responseIsSet = new boolean[FILE_COUNT];
   for (int i = 0; i < FILE_COUNT; ++i) {
     outToPlots[i] = new XYSeries("(EMPTY) " + i);
     dataIsSet[i] = false;
     responseIsSet[i] = false;
   }
  }
  
  /**
   * Takes a loaded miniSEED data series and converts it to a plottable format
   * @param idx The plot (range 0 to FILE_COUNT) to be given new data
   * @param filepath Full address of file to be loaded in
   * @return The miniSEED data, in a plottable format
   */
  public XYSeries setData(int idx, String filepath) {
    DataBlock xy = DataBlockHelper.getXYSeries(filepath);
    
    long start = xy.getStartTime();
    long interval = xy.getInterval();
    long end = start + xy.getData().size()*interval;
    
    // get latest start time and earliest end time, trim data to that length
    long maxStart = Long.max(start, startTime);
    long minEnd = Long.min(end, endTime);
    
    dataBlockArray[idx] = xy;
    
    for (DataBlock db : dataBlockArray) {
      if (db != null) {
        db.trim(maxStart, minEnd);
      }
    }
    
    startTime = maxStart;
    endTime = minEnd;
    
    outToPlots[idx] = xy.toXYSeries();
    
    dataIsSet[idx] = true;
    
    return outToPlots[idx];
  }
  
  public void setResponse(int idx, String filepath) {
    try {
      responses[idx] = new InstrumentResponse(filepath);
      responseIsSet[idx] = true;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public boolean isPlottable() {
    for (int i = 0; i < FILE_COUNT; ++i) {
      if (!dataIsSet[i] || !responseIsSet[i]) {
        return false;
      }
    }
    
    return true;
}
  
  /**
   * Returns the instrument responses as an array, ordered such that the
   * response at index i is the response associated with the DataBlock at i
   * in the array of DataBlocks
   * @return
   */
  public InstrumentResponse[] getResponses() {
    return responses;
  }
  
  /**
   * Returns the set of structures used to hold the loaded miniSeed data sets
   * @return An array of DataBlocks (time series and metadata)
   */
  public DataBlock[] getData() {
    return dataBlockArray;
  }
  
  /**
   * Returns the plottable format of the data held in the arrays at 
   * the specified index
   * @param idx Which of this structure's plottable series should be loaded
   * @return The time series data at given index, to be sent to a chart
   */
  public XYSeries getPlotSeries(int idx) {
    return outToPlots[idx];
  }
}
