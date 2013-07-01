#!/bin/sh

HOMEDIR=./

CLASSPATH="./:$HOMEDIR/classes/:$HOMEDIR/lib/jdom.jar"
export CLASSPATH

cmd="java -Dfile.encoding=UTF8 -ms256m -mx512m fbk.hlt.utility.archive.CGTParser $*" 

runcmd=1

if [ $# = 0 ]; then
	runcmd=0
fi
	
for opt in $cmd
do
	if [ $opt = "-h" ]; then
		runcmd=0
    	break
    elif [ $opt = "--help" ]; then
    	runcmd=0
    	break
	fi
done

if [ $runcmd = 0 ]; then
	echo "Usage: cgtparser [OPTION] <FILE.cgt>"
	java fbk.hlt.utility.archive.CGTParser -ho
else 
	$cmd
fi
