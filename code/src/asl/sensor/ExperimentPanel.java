package asl.sensor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.SeriesException;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class ExperimentPanel extends JPanel implements ActionListener {
 
  private static final long serialVersionUID = -5591522915365766604L;
  // likely need a JFreeChart object of some kind here

  private JButton save;
  private JFreeChart chart; // replace with plot object
  
  private JFileChooser fc; // save image when image save button clicked
  
  private TimeSeriesCollection datasets;
  
  private ExperimentEnum expType; 
          // used to define experiment of each plot object
  
  private Experiment expResult;
  
  public ExperimentPanel(ExperimentEnum exp){
    
    expType = exp;
    // TODO: reset with actual input data
    expResult = ExperimentFactory.createExperiment(exp, null);
    
    createDataset();
    
    chart = ChartFactory.createTimeSeriesChart(
        expType.getName(),
        "Time (sec)",
        "Value",
        datasets,
        true, // include legend
        false, 
        false);
    
    this.setLayout( new BoxLayout(this, BoxLayout.Y_AXIS) );
    
    save = new JButton("Save Plot");
    save.addActionListener(this);

    ChartPanel chartp = new ChartPanel(chart);
    chartp.setMouseZoomable(false);
    
    this.add(chartp);
    this.add(save);
    save.setAlignmentX(Component.CENTER_ALIGNMENT);
    
    fc = new JFileChooser();
    
  }
  
  private void createDataset(){
    datasets = new TimeSeriesCollection();
    
    for(int i = 0; i < DataPanel.FILE_COUNT; ++i) {
      Second step = new Second(0,0,0,1,1,2016);
      double value = 100.0;
      TimeSeries series = new TimeSeries("Dataset " + i);
      for (int j = 0; j < 4000; ++j) {
         try {
            value = value + Math.random( ) - 0.5;                 
            series.add( step, new Double(value) );                 
            step = ( Second ) step.next( );
         } catch ( SeriesException e ) {
            System.err.println("Error adding to series");
         }
      }
      datasets.addSeries(series);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    // TODO Auto-generated method stub
    if( e.getSource() == save ) {
      String ext = ".png";
      fc.addChoosableFileFilter(
          new FileNameExtensionFilter("PNG image (.png)",ext) );
      fc.setFileFilter(fc.getChoosableFileFilters()[1]);
      int returnVal = fc.showSaveDialog(save);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        File selFile = fc.getSelectedFile();
        if( !selFile.getName().endsWith( ext.toLowerCase() ) ) {
          selFile = new File( selFile.toString() + ext);
        }
        try {
          ChartUtilities.saveChartAsPNG(selFile,chart,640,480);
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }
      return;
    }
    
  }
  
}
