#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

PLUGINS_SRC_DIR=$(readlink -m "${SCRIPT_DIR}/../roda-core/roda-plugins/")
PLUGINS_DST_DIR=$(readlink -m "${SCRIPT_DIR}/../jar_plugins/")
if [[ -n $1 ]]; then
	PLUGINS_DST_DIR=(readlink -m "$1")
fi

JARS_WITH_DEPENDENCIES=(`find $PLUGINS_SRC_DIR -name roda-plugin-\*-jar-with-dependencies.jar`)
ALL_JARS=(`find $PLUGINS_SRC_DIR -name roda-plugin-\*.jar`)

JARS=("${ALL_JARS[@]}")

for i in "${JARS_WITH_DEPENDENCIES[@]}"
do :
	SIZE=${#i}-26
	FILE="${i:0:$SIZE}.jar"

	JARS=(${JARS[@]/$FILE})

done

rm -rf "$PLUGINS_DST_DIR"
mkdir -p "$PLUGINS_DST_DIR"

for i in "${JARS[@]}"
do :
	JARBASENAME=$(basename "$i");
	SIZE=${#JARBASENAME}-4
	PLUGIN_FOLDER="${PLUGINS_DST_DIR}/${JARBASENAME:0:$SIZE}"
	mkdir -p "$PLUGIN_FOLDER"
	cp -v -f  "$i" "$PLUGIN_FOLDER"
done

mkdir -p "${PLUGINS_DST_DIR}/shared"
#mv -v "${PLUGINS_DST_DIR}/roda-plugin-common-"* "${PLUGINS_DST_DIR}/shared"
