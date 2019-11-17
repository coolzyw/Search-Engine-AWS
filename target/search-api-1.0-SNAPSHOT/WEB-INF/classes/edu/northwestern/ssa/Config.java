package edu.northwestern.ssa;

public class Config {
    /**
     * This is useful for getting parameters that might be defined either in system properties
     * when running on Elastic Beanstalk) or in environment variables (on command line or IDE)
     */
    public static String getParam(String paramName) {
        String prop = System.getProperty(paramName);
        return (prop != null)? prop : System.getenv(paramName);
    }
}
