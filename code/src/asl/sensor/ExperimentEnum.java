package asl.sensor;

/**
 * Enumerated type defining each kind of test, for GUI and test factory
 * @author akearns
 *
 */
public enum ExperimentEnum {

  // if adding a new test to this, make sure to also create a new extender for
  // experiment and a corresponding constructor call in experimentfactory
  
  ORTHO ("Orthogonality"),
  RGAIN ("Relative gain"),
  NOISE ("Self-noise");
  
  private String name;
  
  ExperimentEnum(String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }
  
}