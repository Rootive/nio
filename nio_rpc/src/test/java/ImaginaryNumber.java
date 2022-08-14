public class ImaginaryNumber {
    private double a;
    private double b;

    public ImaginaryNumber() { }
    public ImaginaryNumber(double a, double b) {
        this.a = a;
        this.b = b;
    }

    public double getA() {
        return a;
    }
    public double getB() {
        return b;
    }
    public void setA(double a) {
        this.a = a;
    }
    public void setB(double b) {
        this.b = b;
    }

    public ImaginaryNumber add(ImaginaryNumber another) {
        return new ImaginaryNumber(a + another.a, b + another.b);
    }
    static public ImaginaryNumber add(ImaginaryNumber a, ImaginaryNumber b) {
        return new ImaginaryNumber(a.a + b.a, a.b + b.b);
    }
    @Override
    public String toString() {
        return a + " + " + b + "i";
    }
}
