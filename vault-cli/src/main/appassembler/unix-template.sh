#!/bin/sh
@LICENSE_HEADER@
#----------------------------------------------------------------------------
#File Vault Start Up Batch script
#
#Required ENV vars:
#------------------
#  JAVA_HOME - location of a JDK home dir
#
#Optional ENV vars
#-----------------
#  VLT_OPTS - parameters passed to the Java VM when running Vault
#    e.g. to debug vault itself, use
#      set VLT_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#
# LINE SEPARATOR
# Because it can be pretty tricky to shove a newline character into a batch or shell script and the text "\n" doesn't get
# interpreted as what you might think by Java, vlt will instead look for LF | CRLF in a system property called vlt.line.separator
# and if it exists, it will set the appropriate Java System property accordingly, e.g. -Dvlt.line.separator=LF
#----------------------------------------------------------------------------

if [ -f /etc/vaultrc ] ; then
  . /etc/vaultrc
fi

if [ -f "$HOME/.vaultrc" ] ; then
  . "$HOME/.vaultrc"
fi

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
BASEDIR=`cd "$PRGDIR/.." >/dev/null; pwd`

@ENV_SETUP@

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
           if [ -z "$JAVA_VERSION" ] ; then
             VERSION=""
           else
             VERSION="-v $JAVA_VERSION"
             echo "Using Java version: $JAVA_VERSION"
           fi
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=`/usr/libexec/java_home $JAVA_VERSION`
           fi
           ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=`java-config --jre-home`
  fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# If a specific java binary isn't specified search for the standard 'java' binary
if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=`which java`
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." 1>&2
  echo "  We cannot execute $JAVACMD" 1>&2
  exit 1
fi

if [ -z "$REPO" ]
then
  REPO="$BASEDIR"/@REPO@
fi

CLASSPATH=$CLASSPATH_PREFIX:@CLASSPATH@

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
  [ -n "$HOME" ] && HOME=`cygpath --path --windows "$HOME"`
  [ -n "$BASEDIR" ] && BASEDIR=`cygpath --path --windows "$BASEDIR"`
  [ -n "$REPO" ] && REPO=`cygpath --path --windows "$REPO"`
fi

EXTRA_JVM_ARGUMENTS="@EXTRA_JVM_ARGUMENTS@"

# try to determine terminal width
COLS=$COLUMNS
if [ -x "/bin/stty" ]; then
    TERM_SIZE=`/bin/stty size 2>/dev/null` 
    for a in ${TERM_SIZE}; do
        COLS=$a
    done
fi

if [ -n "$COLS" ]; then
    EXTRA_JVM_ARGUMENTS="$EXTRA_JVM_ARGUMENTS -Denv.term.width=${COLS}"
fi

exec "$JAVACMD" $VLT_OPTS $EXTRA_JVM_ARGUMENTS \
  -classpath "$CLASSPATH" \
  -Dapp.name="@APP_NAME@" \
  -Dapp.pid="$$" \
  -Dapp.repo="$REPO" \
  -Dvlt.home="$BASEDIR" \
  @MAINCLASS@ \
  @APP_ARGUMENTS@"$@"@UNIX_BACKGROUND@
