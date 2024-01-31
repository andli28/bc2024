package testbot.utils;

// from
// https://www.geeksforgeeks.org/creating-a-user-defined-printable-pair-class-in-java/
// since im lazy
public class Pair<S, T> {
    public S first;
    public T second;

    // constructor for assigning values
    public Pair(S first, T second) {
        this.first = first;
        this.second = second;
    }

    // printing the pair class
    @Override
    public String toString() {
        return first.toString() + "," + second.toString();
    }
}