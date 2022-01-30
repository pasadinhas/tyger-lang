package tyger.codegen;

public class Help {

    public static void main() {
        factorial(10);
    }

    public static int factorial(int n) {
        if (n == 1) return 1;
        return n * factorial(n - 1);
    }

}
