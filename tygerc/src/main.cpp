#include <cstdio>
#include <cstdlib>

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: tygerc <source.ty>\n");
        return 1;
    }

    const char *source_file = argv[1];
    printf("tygerc: compiling %s\n", source_file);

    return 0;
}
