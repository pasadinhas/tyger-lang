# Find llvm-config: prefer a versioned one found via PATH, fall back to Homebrew
LLVM_CONFIG := $(shell command -v llvm-config 2>/dev/null || \
                       command -v llvm-config-18 2>/dev/null || \
                       command -v llvm-config-17 2>/dev/null || \
                       echo /opt/homebrew/opt/llvm/bin/llvm-config)

CXX := $(shell $(LLVM_CONFIG) --bindir)/clang++

LLVM_CXXFLAGS := $(shell $(LLVM_CONFIG) --cxxflags)
LLVM_LDFLAGS  := $(shell $(LLVM_CONFIG) --ldflags)
LLVM_LIBS     := $(shell $(LLVM_CONFIG) --libs core irreader bitwriter native)

CXXFLAGS := -Wall -Wextra -Wno-missing-field-initializers -g $(LLVM_CXXFLAGS)
LDFLAGS  := $(LLVM_LDFLAGS) $(LLVM_LIBS)

SRC_DIR  := src
TEST_DIR := test
BUILD_DIR := build

# Main compiler binary
SRCS := $(wildcard $(SRC_DIR)/*.cpp)
OBJS := $(SRCS:$(SRC_DIR)/%.cpp=$(BUILD_DIR)/%.o)
TARGET := tygerc

# Test binaries (each test_*.cpp is its own binary, linked with all src/*.cpp except main.cpp)
TEST_SRCS  := $(wildcard $(TEST_DIR)/test_*.cpp)
TEST_BINS  := $(TEST_SRCS:$(TEST_DIR)/%.cpp=$(BUILD_DIR)/%)
LIB_SRCS   := $(filter-out $(SRC_DIR)/main.cpp, $(SRCS))
LIB_OBJS   := $(LIB_SRCS:$(SRC_DIR)/%.cpp=$(BUILD_DIR)/%.o)

.PHONY: all clean test

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(OBJS) $(LDFLAGS) -o $@

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) -I$(SRC_DIR) -c $< -o $@

# Each test binary links all lib objects + its own test file
$(BUILD_DIR)/test_%: $(TEST_DIR)/test_%.cpp $(LIB_OBJS) | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) -I$(SRC_DIR) $(LIB_OBJS) $< $(LDFLAGS) -o $@

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

test: $(TEST_BINS)
	@echo ""
	@echo "=== Running unit tests ==="
	@failed=0; \
	for bin in $(TEST_BINS); do \
		echo "--- $$bin ---"; \
		$$bin; \
		if [ $$? -ne 0 ]; then failed=1; fi; \
	done; \
	echo ""; \
	echo "=== Running end-to-end tests ==="; \
	$(MAKE) --no-print-directory e2e || failed=1; \
	if [ $$failed -eq 0 ]; then echo ""; echo "All tests passed."; else echo ""; echo "TESTS FAILED."; exit 1; fi

# End-to-end tests: compile each .ty in test/e2e/ and compare output to .expected
E2E_DIR := test/e2e

e2e: $(TARGET)
	@mkdir -p out
	@failed=0; \
	for src in $(E2E_DIR)/*.ty; do \
		name=$$(basename $$src .ty); \
		expected_file=$(E2E_DIR)/$$name.expected; \
		bin=out/tygerc_e2e_$$name; \
		echo "--- $$name ---"; \
		if ! ./$(TARGET) $$src -o $$bin 2>/dev/null; then \
			echo "  FAIL: compilation error"; failed=1; continue; \
		fi; \
		actual=$$($$bin 2>/dev/null); \
		expected=$$(cat $$expected_file); \
		if [ "$$actual" = "$$expected" ]; then \
			echo "  OK"; \
		else \
			echo "  FAIL:"; \
			echo "    expected: $$expected"; \
			echo "    got:      $$actual"; \
			failed=1; \
		fi; \
		rm -f $$bin; \
	done; \
	if [ $$failed -ne 0 ]; then exit 1; fi

clean:
	rm -rf $(BUILD_DIR) $(TARGET)
