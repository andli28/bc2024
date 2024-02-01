package mainbot.utils;

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