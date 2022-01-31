package tyger.codegen;

public class Help2 {

    public static void main() {
        factorial(10, true, false);
    }

    public static int factorial(int n, boolean test1, boolean test2) {
        boolean c = n == 1;
        int r;
        if (test1 == test2 && c) {
            int j = 1;
            r = j;
        } else {
            int k = 2;
            r = k;
        }
        r = r * 2761239;
        return n + r;
    }

}
