package asl.sensor.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.jfree.data.xy.XYSeries;

import asl.sensor.input.DataBlock;
import asl.sensor.input.InstrumentResponse;

/**
 * Holds the data returned from a power spectral density calculation
 * (The PSD data (without response correction) and frequencies of the FFT)
 * Most methods that either calculate or involve FFT calculations exist here,
 * such as raw PSD calculation, inverse and forward trimmed FFTs,
 * and band-pass filtering.
 * @author akearns
 *
 */
public class FFTResult {
  
  /**
   * Specifies the width of the cosine taper function used in windowing
   */
  private static final double TAPER_WIDTH = 0.10;
  
  public static double[] 
  bandFilter(double[] toFilt, double sps, double low, double high) {
    
    Complex[] fft = simpleFFT(toFilt);
    
    int trim = fft.length/2 + 1;
    
    Complex[] toInvert = new Complex[trim];
    
    double freqDelta = sps/trim;
    
    for (int i = 0; i < trim; ++i) {
      double x = i * freqDelta;
      double scale = 1;
      if ( x < low ) {
        scale = x / low;
      } else if (x > high) {
        scale = 1 - ( x / (sps - high) );
      }
      
      toInvert[i] = fft[i].multiply(scale);
    }
    
    return singleSidedInverseFFT(toInvert, toFilt.length);
    
  }
  
  public static List<Number> 
  bandFilter(List<Number> toFilt, double sps, double low, double high) {
    
    double[] toFFT = new double[toFilt.size()];
    
    for (int i = 0; i < toFFT.length; ++i) {
      toFFT[i] = toFilt.get(i).doubleValue();
    }
    
    toFFT = bandFilter(toFFT, sps, low, high);
    
    List<Number> out = new ArrayList<Number>();
    for (double value : toFFT) {
      out.add(value);
    }
    
    return out;
    
  }
  
  /**
   * Calculates and performs an in-place cosine taper on an incoming data set.
   * Used for windowing for performing FFT.
   * @param dataSet The dataset to have the taper applied to.
   * @return Value corresponding to power loss from application of taper.
   */
  public static double cosineTaper(List<Number> dataSet, double taperW) {
    
    double ramp = taperW * dataSet.size();
    double taper;
    double Wss = 0.0; // represents power loss
    
    for (int i = 0; i < ramp; i++) {
      taper = 0.5 * (1.0 - Math.cos( (double) i * Math.PI / ramp) );
      dataSet.set(i, dataSet.get(i).doubleValue() * taper);
      int idx = dataSet.size()-i-1;
      dataSet.set(idx, dataSet.get(idx).doubleValue() * taper );
      Wss += 2.0 * taper * taper;
    }
    
    Wss += ( dataSet.size() - (2*ramp) );
    
    return Wss;
  }
  
  /**
   * Root funtion for calculating crosspower. Gets spectral calculation of data
   * from inputted data series by calling the spectralCalc function, and then
   * applies the provided responses to that result. This is the Power Spectral
   * Density of the inputted data if both sets are the same.
   * @param data1 First data series
   * @param data2 Second data series
   * @param ir1 Response of instrument producing first series
   * @param ir2 Response of instrument producing second series
   * @return Data structure containing the crosspower result of the two data
   * sets as a complex array and the frequencies matched to them in a double
   * array. 
   */
  public static FFTResult crossPower(DataBlock data1, DataBlock data2,
      InstrumentResponse ir1, InstrumentResponse ir2) {
    
    FFTResult selfPSD = spectralCalc(data1, data2);
    Complex[] results = selfPSD.getFFT();
    double[] freqs = selfPSD.getFreqs();
    Complex[] out = new Complex[freqs.length];
    Complex[] freqRespd1 = ir1.applyResponseToInput(freqs);
    Complex[] freqRespd2 = ir2.applyResponseToInput(freqs);
    freqs = selfPSD.getFreqs();
    
    for (int j = 0; j < freqs.length; ++j) {
      Complex respMagnitude = 
          freqRespd1[j].multiply( freqRespd2[j].conjugate() );
      if (respMagnitude.abs() == 0) {
        respMagnitude = new Complex(Double.MIN_VALUE, 0);
      }
      
      out[j] = results[j].divide(respMagnitude);
      
    }
    
    return new FFTResult(out, freqs);
    
  }
  
  public static double[] demean(double[] dataSet) {
    
    List<Number> dataToProcess = new ArrayList<Number>();
    
    for (double number : dataSet) {
      dataToProcess.add(number);
    }
    
    demeanInPlace(dataToProcess);
    
    double[] out = new double[dataSet.length];
    
    for (int i = 0; i < out.length; ++i) {
      out[i] = dataToProcess.get(i).doubleValue();
    }
    
    return out;
    
  }
  
  public static List<Number> demean(List<Number> dataSet) {
    List<Number> dataOut = new ArrayList<Number>(dataSet);
    demeanInPlace(dataOut);
    return dataOut;
  }
  
  
  /**
   * In-place subtraction of mean from each point in an incoming data set.
   * This is a necessary step in calculating the power-spectral density.
   * @param dataSet The data to have the mean removed from.
   */
  public static void demeanInPlace(List<Number> dataSet) {
    
    // I'm always getting the demeaning tasks, huh?
    
    if(dataSet.size() == 0) return; // shouldn't happen but just in case
    
    double mean = 0.0;
    
    for(Number data : dataSet) {
      mean += data.doubleValue();
    }
    
    mean /= dataSet.size();
    
    for(int i = 0; i < dataSet.size(); ++i) {
      // iterate over index rather than for-each cuz we must replace data
      // ugly syntax because numeric data types are immutable
      dataSet.set(i, dataSet.get(i).doubleValue() - mean);
    }
    
    // test shows this works as in-place method
  }
  
  /**
   * In-place subtraction of trend from each point in an incoming data set.
   * This is a necessary step in calculating the power-spectral density.
   * @param dataSet The data to have the trend removed from.
   */
  public static void detrend(List<Number> dataSet) {
    
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXSqd = 0.0;
    double sumXY = 0.0;
    
    for (int i = 0; i < dataSet.size(); ++i) {
      sumX += (double) i;
      sumXSqd += (double) i * (double) i;
      double value = dataSet.get(i).doubleValue();
      sumXY += value * (double) i;
      sumY += value;
    }
    
    // brackets here so you don't get confused thinking this should be
    // algebraic division (in which case we'd just factor out the size term)
    // 
    
    double del = sumXSqd - ( sumX * sumX / dataSet.size() );
    
    double slope = sumXY - ( sumX * sumY / dataSet.size() );
    slope /= del;
    
    double yOffset = (sumXSqd * sumY) - (sumX * sumXY);
    yOffset /= del * dataSet.size();
    
    for (int i = 0; i < dataSet.size(); ++i) {
      dataSet.set(i, dataSet.get(i).doubleValue() - ( (slope * i) + yOffset) );
    }
    
  }
  
  /**
   * Linear detrend applied to an array of doubles rather than a list.
   * This operation is not done in-place.
   * @param dataSet The double array to be detrended
   * @return Array of doubles with linear detrend removed
   */
  public static double[] detrend (double[] dataSet) {
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXSqd = 0.0;
    double sumXY = 0.0;
    
    for (int i = 0; i < dataSet.length; ++i) {
      sumX += (double) i;
      sumXSqd += (double) i * (double) i;
      double value = dataSet[i];
      sumXY += value * (double) i;
      sumY += value;
    }
    
    // brackets here so you don't get confused thinking this should be
    // algebraic division (in which case we'd just factor out the size term)
    // 
    
    double del = sumXSqd - ( sumX * sumX / dataSet.length );
    
    double slope = sumXY - ( sumX * sumY / dataSet.length );
    slope /= del;
    
    double yOffset = (sumXSqd * sumY) - (sumX * sumXY);
    yOffset /= del * dataSet.length;
    
    double[] detrended = new double[dataSet.length];
    
    for (int i = 0; i < dataSet.length; ++i) {
      detrended[i] = dataSet[i] - ( (slope * i) + yOffset);
    }
    
    return detrended;
  }
  
  /**
   * Collects the data points in the Peterson new high noise model 
   * into a plottable format.
   * Assumes that there is a text file in the .resources folder that contains 
   * the NHNM data points for given input frequencies.
   * @param freqSpace True if the data's x-axis should be units of Hz
   * (otherwise it is units of seconds, the interval between samples)
   * @return Plottable data series representing the NHNM
   */
  public static XYSeries getHighNoiseModel(boolean freqSpace) {
    XYSeries xys = new XYSeries("NHNM");
    try {
      ClassLoader cl = FFTResult.class.getClassLoader();
      InputStream is = cl.getResourceAsStream("NHNM.txt");
      
      BufferedReader fr = new BufferedReader( new InputStreamReader(is) );
      String str = fr.readLine();
      while (str != null) {
        String[] values = str.split("\\s+");
        double x = Double.parseDouble(values[0]); // period, in seconds
        if (x > 1.0E3) {
          break;
        }
        double y = Double.parseDouble(values[1]);
        if (freqSpace) {
          xys.add(1/x, y);
        } else {
          xys.add(x, y);
        }
        
        str = fr.readLine();
      }
      fr.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return xys;
  }
  
  
  /**
   * Collects the data points in the Peterson new low noise model 
   * into a plottable format.
   * Assumes that there is a text file in the .resources folder that contains 
   * the NLNM data points for given input frequencies. ("NLNM.txt")
   * @param freqSpace True if the data's x-axis should be units of Hz
   * (otherwise it is units of seconds, the interval between samples)
   * @return Plottable data series representing the NLNM
   */
  public static XYSeries getLowNoiseModel(boolean freqSpace) {
    XYSeries xys = new XYSeries("NLNM");
    try {
      
      ClassLoader cl = FFTResult.class.getClassLoader();
      
      InputStream is = cl.getResourceAsStream("NLNM.txt");
      
      BufferedReader fr = new BufferedReader( new InputStreamReader(is) );
      String str = fr.readLine();
      while (str != null) {
        String[] values = str.split("\\s+");
        double x = Double.parseDouble(values[0]); // period, in seconds
        if (x > 1.0E3) {
          break;
        }
        double y = Double.parseDouble(values[3]);
        if (freqSpace) {
          xys.add(1/x, y);
        } else {
          xys.add(x, y);
        }
        
        str = fr.readLine();
      }
      fr.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return xys;
  }
  
  /**
   * Do the inverse FFT on the result of a single-sided FFT operation.
   * The negative frequencies are reconstructed as the complex conjugates of
   * the positive corresponding frequencies
   * @param freqDomn Complex array (i.e., the result of a previous FFT calc)
   * @param trim How long the original input data was
   * @return A list of doubles representing the original timeseries of the FFT
   */
  public static double[] singleSidedInverseFFT(Complex[] freqDomn, int trim) {
    FastFourierTransformer fft = 
        new FastFourierTransformer(DftNormalization.UNITARY);
     
    int padding = (freqDomn.length - 1) * 2;
    
    Complex[] padded = new Complex[padding];
    for (int i = 0; i < freqDomn.length; ++i) {
      padded[i] = freqDomn[i];
    }
    for (int i = 1; i < padding/2; ++i) {
      // System.out.println(freqDomn.length+","+i);
      padded[padded.length - i] = padded[i].conjugate();
    }
    
    Complex[] timeSeriesCpx = 
        fft.transform(padded, TransformType.INVERSE);
    
    double[] timeSeries = new double[trim];
    for (int i = 0; i < trim; ++i) {
      timeSeries[i] = timeSeriesCpx[i].getReal();
    }
    
    return timeSeries;
  }
  
  /**
   * Function for padding and returning the result of a forward FFT.
   * This does not trim the negative frequencies of the result; it returns
   * the full FFT result as an array of Complex numbers
   * @param dataIn Array of doubles representing timeseries data
   * @return 
   */
  public static Complex[] simpleFFT(double[] dataIn) {
    
    int padding = 2;
    while ( padding < dataIn.length ) {
      padding *= 2;
    }
    
    double[] toFFT = new double[padding];
    
    for (int i = 0; i < dataIn.length; ++i) {
      toFFT[i] = dataIn[i];
    }
    
    FastFourierTransformer fft = 
        new FastFourierTransformer(DftNormalization.UNITARY);
    
    Complex[] frqDomn = fft.transform(toFFT, TransformType.FORWARD);
    
    return frqDomn;
  }
  
  /**
   * Calculates the FFT of the timeseries data in a DataBlock
   * and returns the positive frequencies resulting from the FFT calculation
   * @param db DataBlock to get the timeseries data from
   * @return Complex array of FFT values and double array of corresponding 
   * frequencies 
   */
  public static FFTResult singleSidedFFT(DataBlock db) {
    
    double[] data = new double[db.size()];
    
    boolean mustFlip = db.needsSignFlip();
    
    for (int i = 0; i < db.size(); ++i) {
      data[i] = db.getData().get(i).doubleValue();
      
      if (mustFlip) {
        data[i] *= -1;
      }
    }
    
    long interval = db.getInterval();

    double sps = TimeSeriesUtils.ONE_HZ_INTERVAL / interval;
    
    data = bandFilter(data, sps, 0.0, 0.1);
    
    data = demean(data);
    
    // data = TimeSeriesUtils.normalize(data);
    
    Complex[] frqDomn = simpleFFT(data);
    
    int padding = frqDomn.length;
    int singleSide = padding/2 + 1;
    
    double period = 1. / TimeSeriesUtils.ONE_HZ_INTERVAL;
    period *= db.getInterval();
    double deltaFrq = 1. / (period * padding);
    
    Complex[] fftOut = new Complex[singleSide];
    double[] frequencies = new double[singleSide];
    
    for (int i = 0; i < singleSide; ++i) {
      fftOut[i] = frqDomn[i];
      frequencies[i] = i * deltaFrq;
    }
    
    return new FFTResult(fftOut, frequencies);
    
  }
  
  /**
   * Helper function to calculate power spectral density / crosspower.
   * Takes in two time series data and produces the windowed FFT over each.
   * The first is multiplied by the complex conjugate of the second.
   * If the two series are the same, this is the PSD of that series. If they
   * are different, this result is the crosspower.
   * The result is smoothed but does not have the frequency response applied,
   * and so does not give a full result -- this is merely a helper function
   * for the crossPower function.
   * @param data1 DataBlock with relevant time series data
   * @param data2 DataBlock with relevant time series data
   * @return A structure with two arrays: an array of Complex numbers 
   * representing the PSD result, and an array of doubles representing the
   * frequencies of the PSD.
   */
  public static FFTResult spectralCalc(DataBlock data1, DataBlock data2) {

    // this is ugly logic here, but this saves us issues with looping
    // and calculating the same data twice
    boolean sameData = data1.getName().equals( data2.getName() );
    
    List<Number> list1 = data1.getData();
    List<Number> list2 = null;
    if (!sameData) {
      list2 = data2.getData();
    }
    
    // divide into windows of 1/4, moving up 1/16 of the data at a time
    
    int range = list1.size()/4;
    int slider = range/4;
    
    // period is 1/sample rate in seconds
    // since the interval data is just that multiplied by a large number
    // let's divide it by that large number to get our period
    
    // shouldn't need to worry about a cast here
    double period = 1.0 / TimeSeriesUtils.ONE_HZ_INTERVAL;
    period *= data1.getInterval();
    
    int padding = 2;
    while (padding < range) {
      padding *= 2;
    }
    
    int singleSide = padding / 2 + 1;
    double deltaFreq = 1. / (padding * period); // TODO: check this line
    
    Complex[] powSpectDens = new Complex[singleSide];
    double wss = 0;
    
    int segsProcessed = 0;
    int rangeStart = 0;
    int rangeEnd = range;
    
    for (int i = 0; i < powSpectDens.length; ++i) {
      powSpectDens[i] = Complex.ZERO;
    }
    
    while ( rangeEnd <= data1.size() ) {
      
      Complex[] fftResult1 = new Complex[singleSide]; // first half of FFT reslt
      Complex[] fftResult2 = null;
      
      if (!sameData) {
        fftResult2 = new Complex[singleSide];
      }
      
      // give us a new list we can modify to get the data of
      List<Number> data1Range = 
          new ArrayList<Number>(
              list1.subList(rangeStart, rangeEnd) );
      List<Number> data2Range = null;
      
      if (!sameData) {
        data2Range = 
            new ArrayList<Number>(
                list2.subList(rangeStart, rangeEnd) );
      }
       
      // double arrays initialized with zeros, set as a power of two for FFT
      // (i.e., effectively pre-padded on initialization)
      double[] toFFT1 = new double[padding];
      double[] toFFT2 = null;
      
      // demean and detrend work in-place on the list
      demeanInPlace(data1Range);
      detrend(data1Range);
      wss = cosineTaper(data1Range, TAPER_WIDTH);
      // TODO: check that we only need the last value of wss
      
      if (!sameData) {
        demeanInPlace(data2Range);
        detrend(data2Range);
        wss = cosineTaper(data2Range, TAPER_WIDTH);
        toFFT2 = new double[padding];
      }
      

      for (int i = 0; i < data1Range.size(); ++i) {
        // no point in using arraycopy -- must make sure each Number's a double
        toFFT1[i] = data1Range.get(i).doubleValue();
        if (!sameData) {
          toFFT2[i] = data2Range.get(i).doubleValue();
        }
      }
      
      FastFourierTransformer fft = 
          new FastFourierTransformer(DftNormalization.STANDARD);


      Complex[] frqDomn1 = fft.transform(toFFT1, TransformType.FORWARD);
      // use arraycopy now (as it's fast) to get the first half of the fft
      System.arraycopy(frqDomn1, 0, fftResult1, 0, fftResult1.length);
      
      Complex[] frqDomn2 = null;
      if (toFFT2 != null) {
        frqDomn2 = fft.transform(toFFT2, TransformType.FORWARD);
        System.arraycopy(frqDomn2, 0, fftResult2, 0, fftResult2.length);
      }
      

      
      for (int i = 0; i < singleSide; ++i) {
        
        Complex val1 = fftResult1[i];
        Complex val2 = val1;
        if (fftResult2 != null) {
          val2 = fftResult2[i];
        }
        
        powSpectDens[i] = 
            powSpectDens[i].add( 
                val1.multiply( 
                    val2.conjugate() ) );
      }
      
      ++segsProcessed;
      rangeStart  += slider;
      rangeEnd    += slider;
      
    }
    
    // normalization time!
    
    double psdNormalization = 2.0 * period / padding; // TODO: check this line
    double windowCorrection = wss / (double) range;
    // it only uses the last value of wss, but that was how the original
    // code was
    
    psdNormalization /= windowCorrection;
    psdNormalization /= segsProcessed; // NOTE: divisor here should be 13
    
    double[] frequencies = new double[singleSide];
    
    for (int i = 0; i < singleSide; ++i) {
      powSpectDens[i] = powSpectDens[i].multiply(psdNormalization);
      frequencies[i] = i * deltaFreq;
    }
    
    // do smoothing over neighboring frequencies; values taken from 
    // asl.timeseries' PSD function
    int nSmooth = 11, nHalf = 5;
    Complex[] psdCFSmooth = new Complex[singleSide];
    
    int iw = 0;
    
    for (iw = 0; iw < nHalf; ++iw) {
      psdCFSmooth[iw] = powSpectDens[iw];
    }
    
    // iw should be icenter of nsmooth point window
    for (; iw < singleSide - nHalf; ++iw){
      int k1 = iw - nHalf;
      int k2 = iw + nHalf;
      
      Complex sumC = Complex.ZERO;
      for (int k = k1; k < k2; ++k) {
        sumC = sumC.add(powSpectDens[k]);
      }
      psdCFSmooth[iw] = sumC.divide((double) nSmooth);
    }
    
    // copy remaining into smoothed array
    for (; iw < singleSide; ++iw) {
      psdCFSmooth[iw] = powSpectDens[iw];
    }
    
    return new FFTResult(psdCFSmooth, frequencies);
    
  }
  
  final private Complex[] transform; // the FFT data
  
  final private double[] freqs; // array of frequencies matching the fft data
  
  /**
   * Instantiate the structure holding an FFT and its frequency range
   * (Used to return data from the spectral density calculations)
   * Holds results of an FFT calculation already performed, usable in return
   * statements
   * @param inPSD Precalculated FFT result for some timeseries
   * @param inFreq Frequencies matched up to each FFT value
   */
  public FFTResult(Complex[] inPSD, double[] inFreq) {
    transform = inPSD;
    freqs = inFreq;
  }

  /**
   * Get the FFT for some sort of previously calculated data
   * @return Array of FFT results, as complex numbers
   */
  public Complex[] getFFT() {
    return transform;
  }
  
  /**
   * Get the frequency range for the (previously calculated) FFT
   * @return Array of frequencies (doubles), matching index to each FFT point
   */
  public double[] getFreqs() {
    return freqs;
  }
  
  
}