JAVA_SOURCES := $(shell find src/main/java -name '*.java')
BUILD_DIR := build/classes
TEST_DIR := build/test-classes
GSON := build/deps/gson-2.11.0.jar
JUNIT := build/deps/junit-platform-console-standalone-1.11.4.jar

.PHONY: start compile test clean

start: compile
	java -cp "$(BUILD_DIR):$(GSON)" cr.ac.tec.jungleattack.server.Main

compile: $(GSON)
	mkdir -p $(BUILD_DIR)
	javac --release 17 -Xlint:all,-classfile -cp "$(GSON)" -d $(BUILD_DIR) $(JAVA_SOURCES)

test: compile $(JUNIT)
	mkdir -p $(TEST_DIR)
	javac --release 17 -Xlint:all,-classfile -cp "$(BUILD_DIR):$(GSON):$(JUNIT)" -d $(TEST_DIR) $$(find src/test/java -name '*.java')
	java -jar $(JUNIT) execute --class-path "$(BUILD_DIR):$(TEST_DIR):$(GSON)" --scan-class-path --fail-if-no-tests --disable-banner
	java --class-path "$(BUILD_DIR):$(GSON)" tools/PathProbe.java

$(GSON):
	mkdir -p build/deps
	curl --fail --location --retry 2 https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar -o $@.tmp
	mv $@.tmp $@

$(JUNIT):
	mkdir -p build/deps
	curl --fail --location --retry 2 https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar -o $@.tmp
	mv $@.tmp $@

clean:
	rm -rf build