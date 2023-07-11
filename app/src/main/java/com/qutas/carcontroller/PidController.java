package com.qutas.carcontroller;

public class PidController {
    private final double p, i, d;
    private double accumulated = 0;
    private double prev = 0;
    private double setpoint = 0;
    public PidController(double p, double i, double d){
        this.p = p;
        this.i = i;
        this.d = d;
    }
    public void SetSetpoint(double setpoint)
    {
        this.setpoint = setpoint;
    }
    public double Run(double input, double dT)
    {
        double error = setpoint - input;
        double pFactor = error * p;
        accumulated += error * dT;
        double iFactor = accumulated * i;
        double dFactor = ((prev - input) / dT) * d;
        prev = input;
        return (pFactor + iFactor + dFactor);
    }
}
