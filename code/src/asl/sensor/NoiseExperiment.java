package asl.sensor;

import org.apache.commons.math3.complex.Complex;
import org.jfree.data.xy.XYSeries;

/**
 * Produces the data for a self-noise test. Calculates PSD to get cross-power.
 * Based on code in the seedscan timeseries package, see
 * https://github.com/usgs/seedscan/tree/master/src/main/java/asl/timeseries
 * @author akearns, jholland 
 *
 */
public class NoiseExperiment extends Experiment {
  

  
  /**
   * Instantiates a noise experiment -- axis titles and scales
   */
  public NoiseExperiment() {
    super();

  }

  /**
   * Generates power spectral density of each inputted file, and calculates
   * self-noise based on that result.
   * The overhead view is as follows: 
   * Take a window of size 1/4 incrementing through 1/16 of the data and
   * calculate the FFT of that region. Average these results together.
   * Apply the magnitude of the frequency response (relative to the FFT indices)
   * to that result and then take the complex conjugate. 
   * This produces the PSD plots.
   * Then, take the cross-powers of each of the terms (same calculation, but
   * multiply one result by the complex conjugate of the other), producing the
   * remaining terms for the formula for the self-noise results.
   */
  @Override
  void backend(DataStore ds, boolean freqSpace) {
    
    DataBlock[] dataIn = ds.getData();
    InstrumentResponse[] responses = ds.getResponses();
    
    xySeriesData.setAutoWidth(true);
    
    // TODO: make sure (i.e., when reading in data) that series lengths' match
    // rather than throwing the exceptions here
    Complex[][] spectra = new Complex[DataStore.FILE_COUNT][];
    Complex[][] freqRespd = new Complex[DataStore.FILE_COUNT][];
    double[] freqs = new double[1]; // initialize to prevent later errors
    
    // initialize the values above to have relevant data
    for (int i = 0; i < dataIn.length; ++i) {
      spectra[i] = ds.getPSD(i).getFFT();
      InstrumentResponse ir = responses[i];
      freqs = ds.getPSD(i).getFreqs();
      freqRespd[i] = ir.applyResponseToInput( freqs );
    }
    
    addToPlot(ds, freqSpace, xySeriesData);
    
    // spectra[i] is crosspower pii, now to get pij terms for i!=j
    FFTResult fft = 
        FFTResult.crossPower(dataIn[0], dataIn[2], responses[0], responses[2]);
    Complex[] c13 = fft.getFFT();
    fft = 
        FFTResult.crossPower(dataIn[1], dataIn[0], responses[1], responses[0]);
    Complex[] c21 = fft.getFFT();
    fft = 
        FFTResult.crossPower(dataIn[1], dataIn[2], responses[1], responses[2]);
    Complex[] c23 = fft.getFFT();
    
    // WIP: use PSD results to get noise at each point see spectra
    XYSeries[] noiseSeriesArr = new XYSeries[dataIn.length];
    for(int j = 0; j < dataIn.length; ++j) {
      // initialize each xySeries with proper name for the data
      noiseSeriesArr[j] = new XYSeries( "Noise " + dataIn[j].getName() );
    }
    
    for (int i = 1; i < freqs.length; ++i) {
        if (1/freqs[i] > 1.0E3){
          continue;
        }
        
        // Complex f1 = freqRespd[0][i];
        // Complex f2 = freqRespd[1][i];
        // Complex f3 = freqRespd[2][i];
        
        Complex p11 = spectra[0][i];
        Complex p22 = spectra[1][i];
        Complex p33 = spectra[2][i];

        Complex p13 = c13[i];
        Complex p21 = c21[i];
        Complex p23 = c23[i];
        
        // nii = pii - pij*hij
        Complex n11 = 
            p11.subtract(
                p21.multiply(p13).divide(p23) );
        n11 = n11.multiply(Math.pow(2*Math.PI*freqs[i],4));
        
        Complex n22 = 
            p22.subtract(
                ( p23.conjugate() ).multiply(p21).divide( p13.conjugate() ) );
        n22 = n22.multiply(Math.pow(2*Math.PI*freqs[i],4));
        
        Complex n33 = 
            p33.subtract(
                p23.multiply( p13.conjugate() ).divide( p21 ) );
        n33 = n33.multiply(Math.pow(2*Math.PI*freqs[i],4));
        
        // now get magnitude and convert to dB
        double plot1 = 10*Math.log10( n11.abs() );
        double plot2 = 10*Math.log10( n22.abs() );
        double plot3 = 10*Math.log10( n33.abs() );
        if (Math.abs(plot1) != Double.POSITIVE_INFINITY) {
          if (freqSpace) {
            noiseSeriesArr[0].add(freqs[i], plot1);
          } else {
            noiseSeriesArr[0].add(1/freqs[i], plot1);
          }
        }
        if (Math.abs(plot2) != Double.POSITIVE_INFINITY) {
          if (freqSpace) {
            noiseSeriesArr[1].add(freqs[i], plot2);
          } else {
            noiseSeriesArr[1].add(1/freqs[i], plot2);
          }
        }
        if (Math.abs(plot3) != Double.POSITIVE_INFINITY) {
          if (freqSpace) {
            noiseSeriesArr[2].add(freqs[i], plot3);
          } else {
            noiseSeriesArr[2].add(1/freqs[i], plot3);
          }
        }
    }
    
    for (XYSeries noiseSeries : noiseSeriesArr) {
      xySeriesData.addSeries(noiseSeries);
    }
    
    xySeriesData.addSeries( FFTResult.getLowNoiseModel(freqSpace, this) );

  }

}
