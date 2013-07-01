#!/bin/tcsh

#setenv JAVA_HOME /marquez0/tcc/cgirardi/java/jdk1.7.0_21/

# compile with:
#$JAVA_HOME/bin/javac -cp . -d classes/ -sourcepath src/ src/main/java/org/fbk/hlt/newsreader/ZipManager.java 

# example of running
#./zip2cgt.sh -O test/output/ test/input/lexisnexis-example.zip

$JAVA_HOME/bin/java -Dfile.encoding=UTF8 -cp "classes/:lib/jdom.jar" main.java.org.fbk.hlt.newsreader.ZipManager $*
