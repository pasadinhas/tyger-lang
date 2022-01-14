antlr4:
	java -Xmx500M -cp "./antlr-4.9-complete.jar:${CLASSPATH}" org.antlr.v4.Tool Tyger.g4

tree:
	java -Xmx500M -cp "./antlr-4.9-complete.jar:${CLASSPATH}" org.antlr.v4.gui.TestRig Tyger r -tree

gui:
	java -Xmx500M -cp "./antlr-4.9-complete.jar:${CLASSPATH}" org.antlr.v4.gui.TestRig Tyger r -gui

compile: antlr4
	javac -cp "./antlr-4.9-complete.jar:${CLASSPATH}" *.java