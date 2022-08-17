import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class Color {
    int r;
    int g;
    int b;

    public Color() { }
    public Color(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public int getR() {
        return r;
    }
    public int getG() {
        return g;
    }
    public int getB() {
        return b;
    }
    public void setR(int r) {
        this.r = r;
    }
    public void setG(int g) {
        this.g = g;
    }
    public void setB(int b) {
        this.b = b;
    }

}

public class Dog implements DogInterface {
    String name;
    int age;
    Color color;

    public Dog() { }
    public Dog(String name, int age, Color color) {
        this.name = name;
        this.age = age;
        this.color = color;
    }

    public Dog info() {
        return this;
    }
    public Dog fork(String newName) {
        return new Dog(newName, age, color);
    }
    public boolean isTheSameAgeWith(DogInterface another) {
        return age == another.info().getAge();
    }
    public boolean isTheSameAgeWith(Dog another) {
        return age == another.age;
    }

    public String getName() {
        return name;
    }
    public int getAge() {
        return age;
    }
    public Color getColor() {
        return color;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public void setColor(Color color) {
        this.color = color;
    }
    @Override
    public String toString() {
        String ret = null;
        try {
            ret = new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return ret;
    }
}

interface DogInterface {
    Dog info();
    Dog fork(String newName);
    boolean isTheSameAgeWith(DogInterface another);
    boolean isTheSameAgeWith(Dog another);
}
