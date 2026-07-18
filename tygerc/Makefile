LLVM_CONFIG := /opt/homebrew/opt/llvm/bin/llvm-config
CXX         := /opt/homebrew/opt/llvm/bin/clang++

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
	@echo "=== Running tests ==="
	@failed=0; \
	for bin in $(TEST_BINS); do \
		echo "--- $$bin ---"; \
		$$bin; \
		if [ $$? -ne 0 ]; then failed=1; fi; \
	done; \
	if [ $$failed -eq 0 ]; then echo ""; echo "All tests passed."; else echo ""; echo "TESTS FAILED."; exit 1; fi

clean:
	rm -rf $(BUILD_DIR) $(TARGET)
