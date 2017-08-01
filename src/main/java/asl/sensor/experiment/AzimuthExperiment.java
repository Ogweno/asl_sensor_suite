package asl.sensor.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import 
org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import asl.sensor.input.DataBlock;
import asl.sensor.input.DataStore;
import asl.sensor.utils.FFTResult;
import asl.sensor.utils.NumericUtils;
import asl.sensor.utils.TimeSeriesUtils;

/**
 * More specific javadoc will be incoming, but for now a brief explanation
 * of the angle conventions used
 * The program attempts to fit known-orthogonal sensors of unknown azimuth to a
 * reference sensor assumed to be north. The rotation angle between the
 * reference sensor and the unknown components is solved for via least-squares
 * using the coherence calculation of the rotated and reference signal.
 * The resulting angle, then, is the clockwise rotation from the reference.
 * If the angle of the reference is zero (i.e., pointing directly north),
 * the result of this calculation SHOULD be the value of the azimuth, using
 * a clockwise rotation convention.
 * If the reference sensor is itself offset X degrees clockwise from
 * north, the azimuth is the sum of the estimated angle difference between
 * the sensors plus the offset from north.
 * This calculation is mostly based on Ringler, Edwards, et al.,
 * 'Relative azimuth inversion by way of damped maximum correlation estimates',
 * Elsevier Computers and Geosciences 43 (2012)
 * but using coherence maximization rather than correlation to find optimized
 * angles.
 * @author akearns
 *
 */
public class AzimuthExperiment extends Experiment {
  
  /**
   * Check if data is aligned antipolar or not (signs of data are inverted).
   * This is done by getting a range of data and seeing whether more data
   * have the same sign or different signs. This is necessary because the
   * data is fit by coherence, which is optimized by both the angle x and the
   * angle 180 + x, so the solver chooses the closest one to the initial angle.
   * @param rot Data that has been rotated and may be 180 degrees off from
   * correct orientation
   * @param ref Data that is to be used as reference with known orientation
   * @param len Amount of data to be analysed for sign matching
   * @return True if more data analysed has opposite signs than matching signs
   * (i.e., one signal is positive and one is negative)
   */
  public static boolean 
  alignedAntipolar(List<Number> rot, List<Number> ref, int len) {
    int numSameSign = 0; int numDiffSign = 0;
    for (int i = 0; i < len; ++i) {
      int sigRot = (int) Math.signum( (double) rot.get(i) );
      int sigRef = (int) Math.signum( (double) ref.get(i) );
      
      if (sigRot - sigRef == 0) {
        ++numSameSign;
      } else {
        ++numDiffSign;
      }
    }
    
    return numSameSign < numDiffSign;
    
  }
  private double offset = 0.;
  
  private double angle, uncert;
  private double[] freqs;
  
  private double[] coherence;
  
  private boolean simpleCalc; // used for nine-noise calculation
  
  public AzimuthExperiment() {
    super();
    simpleCalc = false;
  }
  
  @Override
  protected void backend(final DataStore ds) {
    
    // assume the first two are the reference and the second two are the test
    // we just need the timeseries, don't actually care about response
    DataBlock testNorthBlock = new DataBlock( ds.getXthLoadedBlock(1) );
    DataBlock testEastBlock = new DataBlock( ds.getXthLoadedBlock(2) );
    DataBlock refNorthBlock = new DataBlock( ds.getXthLoadedBlock(3) );
    
    dataNames.add( testNorthBlock.getName() );
    dataNames.add( testEastBlock.getName() );
    dataNames.add( refNorthBlock.getName() );
    
    List<Number> testNorth = new ArrayList<Number>( testNorthBlock.getData() );
    String northName = testNorthBlock.getName();
    List<Number> testEast = new ArrayList<Number>( testEastBlock.getData() );
    String eastName = testEastBlock.getName();
    List<Number> refNorth = new ArrayList<Number>( refNorthBlock.getData() );
    String refName = refNorthBlock.getName();
    
    TimeSeriesUtils.detrend(testNorth);
    TimeSeriesUtils.detrend(testEast);
    TimeSeriesUtils.detrend(refNorth);
    
    // originally had normalization step here, but that harmed the estimates
    
    double sps = TimeSeriesUtils.ONE_HZ_INTERVAL / testNorthBlock.getInterval();
    double low = 1./8;
    double high = 1./4;
    
    testNorth = FFTResult.bandFilter(testNorth, sps, low, high);
    testEast = FFTResult.bandFilter(testEast, sps, low, high);
    refNorth = FFTResult.bandFilter(refNorth, sps, low, high);
    
    testNorthBlock.setData(testNorth);
    testEastBlock.setData(testEast);
    refNorthBlock.setData(refNorth);
    
    MultivariateJacobianFunction jacobian = 
        getJacobianFunction(testNorthBlock, testEastBlock, refNorthBlock);
    
    // want mean coherence to be as close to 1 as possible
    RealVector target = MatrixUtils.createRealVector(new double[]{1.});
    
    
    /*
    // first is rel. tolerance, second is abs. tolerance
    ConvergenceChecker<LeastSquaresProblem.Evaluation> cv = 
        new EvaluationRmsChecker(1E-3, 1E-3);
    */
    
    LeastSquaresProblem findAngleY = new LeastSquaresBuilder().
        start(new double[] {0}).
        model(jacobian).
        target(target).
        maxEvaluations(Integer.MAX_VALUE).
        maxIterations(Integer.MAX_VALUE).
        lazyEvaluation(false).
        //checker(cv).
        build();
    
    LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer().
        withCostRelativeTolerance(1E-15).
        withParameterRelativeTolerance(1E-15);
    
    LeastSquaresOptimizer.Optimum optimumY = optimizer.optimize(findAngleY);
    RealVector angleVector = optimumY.getPoint();
    double tempAngle = angleVector.getEntry(0);
    
    String newStatus = "Found initial guess for angle";
    fireStateChange(newStatus);
    
    // how much data we need (i.e., iteration length) to check 10 seconds
    // used when checking if alignment is off by 180 degrees
    int tenSecondsLength = (int)  ( testNorthBlock.getSampleRate() * 10 ) + 1;
    int hundredSecLen = tenSecondsLength * 10;
    
    if (simpleCalc) {
      // used for orthogonality & multi-component self-noise and gain
      // where a 'pretty good' estimate of the angle is all we need
      // just stop here, don't do windowing
      angle = tempAngle;
      
      // check if we need to rotate by 180 degrees
      DataBlock rot = 
          TimeSeriesUtils.rotate(testNorthBlock, testEastBlock, angle);
      List<Number> rotTimeSeries = rot.getData();
      List<Number> refTimeSeries = refNorthBlock.getData();
      
      if ( alignedAntipolar(rotTimeSeries, refTimeSeries, tenSecondsLength) ) {
        angle += Math.PI; // still in radians
      }
      
      return;
    }
    
    // angleVector is our new best guess for the azimuth
    // now let's cut the data into 2000-sec windows with 500-sec overlap
    // store the angle and resulting correlation of each window
    // and then take the best-correlation angles and average them
    long start = testNorthBlock.getStartTime();
    long end = testNorthBlock.getEndTime();
    long timeRange = end - start;
    
    // first double -- angle estimate over window
    // second double -- coherence from that estimate over the window
    Map<Long, Pair<Double,Double>> angleCoherenceMap = 
        new HashMap<Long, Pair<Double, Double>> ();
    List<Double> sortedCoherence = new ArrayList<Double>();
    
    final long twoThouSecs = 2000L * TimeSeriesUtils.ONE_HZ_INTERVAL; 
    // 1000 ms per second, range length
    final long fiveHundSecs = twoThouSecs / 4L; // distance between windows
    int numWindows = (int) ( (timeRange - twoThouSecs) / fiveHundSecs);
    // look at 2000s windows, sliding over 500s of data at a time
    for (int i = 0; i < numWindows; ++i) {
      StringBuilder sb = new StringBuilder();
      sb.append("Fitting angle over data in window ");
      sb.append(i + 1);
      sb.append(" of ");
      sb.append(numWindows);
      newStatus = sb.toString();
      
      fireStateChange(newStatus);
      
      if (timeRange < 2 * twoThouSecs) {
        break;
      }
      
      long wdStart = fiveHundSecs * i + start; // start of 500s-sliding window
      long wdEnd = wdStart + twoThouSecs; // end of window (2000s long)
      
      DataBlock testNorthWindow = new DataBlock(testNorthBlock, wdStart, wdEnd);
      DataBlock testEastWindow = new DataBlock(testEastBlock, wdStart, wdEnd);
      DataBlock refNorthWindow = new DataBlock(refNorthBlock, wdStart, wdEnd);
      
      jacobian = 
          getJacobianFunction(testNorthWindow, testEastWindow, refNorthWindow);
      
      LeastSquaresProblem findAngleWindow = new LeastSquaresBuilder().
          start(new double[]{tempAngle}).
          model(jacobian).
          target(target).
          maxEvaluations(Integer.MAX_VALUE).
          maxIterations(Integer.MAX_VALUE).
          lazyEvaluation(false).
          // checker(cv).
          build();
            
      optimumY = optimizer.optimize(findAngleWindow);
      
      RealVector angleVectorWindow = optimumY.getPoint();
      double angleTemp = angleVectorWindow.getEntry(0);
      
      double coherenceAvg = 0;
      for (double cVal : coherence) {
        coherenceAvg += cVal;
      }
      coherenceAvg /= coherence.length;
      
      angleCoherenceMap.put(
          wdStart, new Pair<Double, Double>(angleTemp, coherenceAvg) );
      sortedCoherence.add(coherenceAvg);
    }
    
    if (angleCoherenceMap.size() < 1) {
      fireStateChange("Window size too small for good angle estimation...");
      angle = Math.toDegrees( angleVector.getEntry(0) );
    } else {
      // get the best-coherence estimations of angle and average them
      Collections.sort(sortedCoherence);
      int maxBoundary = Math.max(5, sortedCoherence.size() * 3 / 20);
      sortedCoherence = sortedCoherence.subList(0, maxBoundary);
      Set<Double> acceptableCoherences = new HashSet<Double>(sortedCoherence);
      
      // store values for use in 
      List<Double> acceptedVals = new ArrayList<Double>();
      
      double averageAngle = 0.;
      int coherenceCount = 0;
      
      for (Pair<Double, Double> angCoherePair : angleCoherenceMap.values()) {
        double angleTemp = angCoherePair.getFirst();
        double coherence = angCoherePair.getSecond();
        if ( acceptableCoherences.contains(coherence) ) {
          averageAngle += angleTemp;
          acceptedVals.add(angleTemp);
          ++coherenceCount;
        }
      }
      
      averageAngle /= coherenceCount;
      
      uncert = 0.;
      
      // now get standard deviation
      for (double angle : acceptedVals) {
        uncert += Math.pow(angle - averageAngle, 2);
      }
      
      uncert = Math.sqrt( uncert / (coherenceCount) );
      uncert *= 2; // two-sigma gets us 95% confidence interval
      
      // do this calculation to get plot of freq/coherence, a side effect
      // of running evaluation at the given point; this will be plotted
      RealVector angleVec = 
          MatrixUtils.createRealVector(new double[]{averageAngle});
      findAngleY.evaluate(angleVec);
      
      double tau = NumericUtils.TAU;
      angle = ( (averageAngle % tau) + tau ) % tau;
      
    }

    fireStateChange("Solver completed, checking if anti-polar...");
    
    // solver produces angle of x, 180+x that is closer to reference
    // if angle is ~180 degrees away from reference in reality, then the signal
    // would be inverted from the original. so get 10 seconds of data and check
    // to see if the data is all on the same side of 0.
    
    DataBlock rot = 
        TimeSeriesUtils.rotate(testNorthBlock, testEastBlock, angle);
    
    List<Number> rotTimeSeries = rot.getData();
    List<Number> refTimeSeries = refNorthBlock.getData();
    
    if ( alignedAntipolar(rotTimeSeries, refTimeSeries, hundredSecLen) ) {
      angle += Math.PI; // still in radians
      angle = angle % NumericUtils.TAU; // keep between 0 and 360
    }
    
    double angleDeg = Math.toDegrees(angle);
    
    XYSeries ref = new XYSeries(northName + " rel. to reference");
    ref.add(offset + angleDeg, 0);
    ref.add(offset + angleDeg, 1);
    XYSeries set = new XYSeries(eastName + " rel. to reference");
    set.add(offset + angleDeg + 90, 1);
    set.add(offset + angleDeg + 90, 0);
    XYSeries fromNorth = new XYSeries (refName + " location");
    fromNorth.add(offset, 1);
    fromNorth.add(offset, 0);

    // xySeriesData = new XYSeriesCollection();
    XYSeriesCollection xysc = new XYSeriesCollection();
    xysc.addSeries(ref);
    xysc.addSeries(set);
    xysc.addSeries(fromNorth);
    xySeriesData.add(xysc);
    
    XYSeries coherenceSeries = new XYSeries("Per-freq. coherence of best-fit");
    for (int i = 0; i < freqs.length; ++i) {
      coherenceSeries.add(freqs[i], coherence[i]);
    }
    
    xysc = new XYSeriesCollection();
    XYSeries timeMapAngle = new XYSeries("Best-fit angle per window");
    XYSeries timeMapCoherence = new XYSeries("Coherence estimate per window");
    xysc.addSeries(timeMapAngle);
    xysc.addSeries(timeMapCoherence);
    
    for ( long time : angleCoherenceMap.keySet() ) {
      double angle = angleCoherenceMap.get(time).getFirst();
      double coherence = angleCoherenceMap.get(time).getSecond();
      timeMapCoherence.add(time, coherence);
      timeMapAngle.add( time, Math.toDegrees(angle) );
    }
    
    xySeriesData.add( new XYSeriesCollection(coherenceSeries) );
    xySeriesData.add( new XYSeriesCollection(timeMapAngle) );
    xySeriesData.add( new XYSeriesCollection(timeMapCoherence) );
  }
  
  /**
   * Get the uncertainty of the angle 
   * @return
   */
  public double getUncertainty() {
    return Math.toDegrees(uncert);
  }
  
  @Override
  public int blocksNeeded() {
    return 3;
  }

  /**
   * Return the fit angle calculated by the backend in degrees
   * @return angle result in degrees
   */
  public double getFitAngle() {
    return Math.toDegrees(angle);
  }
  
  /**
   * Return the fit angle calculated by the backend in radians
   * @return angle result in radians
   */
  public double getFitAngleRad() {
    return angle;
  }
  
  /**
   * Returns the jacobian function for this object given input data blocks.
   * The data blocks are set here because they are what will be rotated by
   * the given angle (the realvector point is the angle).
   * This allows us to fix the datablocks in question while varying the angle,
   * and calling the jacobian on different datablocks, such as when finding
   * the windows of maximum coherence.
   * @param db1 Test north data block
   * @param db2 Test east data block
   * @param db3 Ref. north data block
   * @return jacobian function to fit an angle of max coherence of this data
   */
  private MultivariateJacobianFunction 
  getJacobianFunction(DataBlock db1, DataBlock db2, DataBlock db3) {
    
    // make my func the j-func, I want that func-y stuff
    MultivariateJacobianFunction jFunc = new MultivariateJacobianFunction() {

      final DataBlock finalTestNorthBlock = db1;
      final DataBlock finalTestEastBlock = db2;
      final DataBlock finalRefNorthBlock = db3;

      public Pair<RealVector, RealMatrix> value(final RealVector point) {
        return jacobian(point, 
            finalRefNorthBlock, 
            finalTestNorthBlock, 
            finalTestEastBlock);
      }
    };
    
    return jFunc; 
  }
  
  public double getOffset() {
    return ( (offset % 360) + 360 ) % 360;
  }
  
  @Override
  public boolean hasEnoughData(DataStore ds) {
    for (int i = 0; i < blocksNeeded(); ++i) {
      if ( !ds.blockIsSet(i) ) {
        return false;
      }
    }
    return true;
  }

  /**
   * Jacobian function for the azimuth solver. Takes in the directional
   * signal components (DataBlocks) and the angle to evaluate at and produces
   * the coherence at that point and the forward difference
   * @param point Current angle
   * @param refNorth Reference sensor, facing north
   * @param testNorth Test sensor, facing approximately north
   * @param testEast Test sensor, facing approximately east and orthogonal to
   * testNorth
   * @return Coherence (RealVector) and forward difference 
   * approximation of the Jacobian (RealMatrix) at the current angle
   */
  private Pair<RealVector, RealMatrix> jacobian(
      final RealVector point, 
      final DataBlock refNorth,
      final DataBlock testNorth, 
      final DataBlock testEast) {
    
    double theta = ( point.getEntry(0) );
    
    double diff = 1E-2;
    
    double lowFreq = 1./18.;
    double highFreq = 1./3.;
    
    DataBlock testRotated = TimeSeriesUtils.rotate(testNorth, testEast, theta);
    
    FFTResult crossPower = FFTResult.spectralCalc(refNorth, testRotated);
    FFTResult rotatedPower = FFTResult.spectralCalc(testRotated, testRotated);
    FFTResult refPower = FFTResult.spectralCalc(refNorth, refNorth);
    
    freqs = crossPower.getFreqs();
    
    Complex[] crossPowerSeries = crossPower.getFFT();
    Complex[] rotatedSeries = rotatedPower.getFFT();
    Complex[] refSeries = refPower.getFFT();
    
    coherence = new double[crossPowerSeries.length];
    
    for (int i = 0; i < crossPowerSeries.length; ++i) {
      Complex conj = crossPowerSeries[i].conjugate();
      Complex numerator = crossPowerSeries[i].multiply(conj);
      Complex denom = rotatedSeries[i].multiply(refSeries[i]);
      coherence[i] = numerator.divide(denom).getReal();
    }
    
    double peakVal = Double.NEGATIVE_INFINITY;
    double peakFreq = 0;
    
    for (int i = 0; i < freqs.length; ++i) {
      if (freqs[i] < lowFreq) {
        continue;
      } else if (freqs[i] > highFreq) {
        break;
      }
      if (peakVal < coherence[i]) {
        peakVal = coherence[i];
        peakFreq = freqs[i];
      }
    }
    
    if (peakFreq / 2 > lowFreq) {
      lowFreq = peakFreq / 2.;
    }
    
    if (peakFreq * 2 < highFreq) {
      highFreq = peakFreq * 2.;
    }
    
    double meanCoherence = 0.;
    int samples = 0;
    
    for (int i = 0; i < freqs.length; ++i) {
      if (freqs[i] < highFreq && freqs[i] > lowFreq) {
        meanCoherence += coherence[i];
        ++samples;
      }
    }
    
    meanCoherence /= samples;
    
    RealVector curValue = 
        MatrixUtils.createRealVector(new double[]{meanCoherence});
    
    double thetaDelta = theta + diff;
    DataBlock rotateDelta = 
        TimeSeriesUtils.rotate(testNorth, testEast, thetaDelta);
    
    crossPower = FFTResult.spectralCalc(refNorth, rotateDelta);
    rotatedPower = FFTResult.spectralCalc(rotateDelta, rotateDelta);
    crossPowerSeries = crossPower.getFFT();
    rotatedSeries = rotatedPower.getFFT();
    
    double fwdMeanCoherence = 0.;
    samples = 0;
    double[] fwdCoherence = new double[crossPowerSeries.length];
    
    for (int i = 0; i < crossPowerSeries.length; ++i) {
      Complex conj = crossPowerSeries[i].conjugate();
      Complex numerator = crossPowerSeries[i].multiply(conj);
      Complex denom = rotatedSeries[i].multiply(refSeries[i]);
      fwdCoherence[i] = numerator.divide(denom).getReal();
      
      if (freqs[i] < highFreq && freqs[i] > lowFreq) {
        fwdMeanCoherence += fwdCoherence[i];
        ++ samples;
      }
      
    }
    
    fwdMeanCoherence /= (double) samples;
    double deltaMean = (fwdMeanCoherence - meanCoherence) / diff;
    
    // System.out.println(deltaMean);
    
    double[][] jacobianArray = new double[1][1];
    jacobianArray[0][0] = deltaMean;
    
    // we have only 1 variable, so jacobian is a matrix w/ single column
    RealMatrix jbn = MatrixUtils.createRealMatrix(jacobianArray);
    
    return new Pair<RealVector, RealMatrix>(curValue, jbn);
  }

  /**
   * Set the angle offset for the reference sensor (degrees from north)
   * @param newOffset Degrees from north that the reference sensor points
   */
  public void setOffset(double newOffset) {
    offset = newOffset;
  }
  
  /**
   * Used to set a simple calculation of rotation angle, such as for
   * nine-input self-noise. This is the case where the additional windowing
   * is NOT done, and the initial least-squares guess gives us an answer.
   * When creating an instance of this object, this is set to false and only
   * needs to be explicitly set when a simple calculation is desired.
   * @param isSimple True if a simple calculation should be done
   */
  public void setSimple(boolean isSimple) {
    simpleCalc = isSimple;
  }
  
}