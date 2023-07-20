#!/usr/bin/env bash

ARTIFACT_DIR_NAME="versions_artifacts"

# Before this script runs, the job downloads the artifacts into files with the following example structure:
#
#   versions_artifacts/
#       12/
#           express: (next lines are the data inside the file)
#               4.17.2
#               !4.17.3
#           mongoose:
#               6.4.4
#               3.9.7
#       14/
#           express:
#               4.17.2
#               !4.17.3
#           mongoose:
#               3.9.7
#               6.4.4
#
# Each file contains the original supported versions and the results from the tests in the previous job.

calculate_runtime_to_tested_versions() {
    instrumentation_name="$1"
    shift 1
    runtimes=("$@")

    declare -A tested_versions=()
    for runtime in "${runtimes[@]}"; do
        content="$(cat "$ARTIFACT_DIR_NAME/$runtime/$instrumentation_name")"
        tested_versions[$runtime]="${content//\!}"
        # Set on the variable for use in handle_dependency
        runtime_to_tested_versions[$runtime]="$content"
    done

    all_versions="${tested_versions[${runtimes[0]}]}"

    for tested_versions in "${tested_versions[@]}"; do
        if [[ "${all_versions[*]}" != "$(echo "$tested_versions" | awk '{ print $1 }')" ]]; then
            echo "Got different versions from different runtimes"
            exit 1
        fi
    done
}

add_version_to_file() {
    origin_path="$1"
    version="$2"
    supported="$3"

    if [[ "$supported" == true ]]; then
        echo "$version" >> "$origin_path"
    else
        echo "!$version" >> "$origin_path"
    fi
}

handle_dependency() {
    instrumentation_name="$1"
    origin_path="$2"
    shift 2
    runtimes=("$@")

    echo "working on: $instrumentation_name"

    declare -A runtime_to_tested_versions=()
    calculate_runtime_to_tested_versions "$instrumentation_name" "${runtimes[@]}"

    reference_lines=(${runtime_to_tested_versions[${runtimes[0]}]})

    for ((i = 0; i < ${#reference_lines[@]}; i++)); do
        version="${reference_lines[$i]}"
        supported=true
        for runtime in "${runtimes[@]}"; do
            current_lines=(${runtime_to_tested_versions[$runtime]})
            for ((j = i; j < ${#current_lines[@]}; j++)); do
                current_line="${current_lines[$j]}"
                if [[ "$current_line" != "$version" ]]; then
                    supported=false
                    break
                else
                    # If a versions start with !, set supported to false
                    if [[ "$current_line" == \!* ]]; then
                        supported=false
                    fi
                    # Though we're looping, we only need to check the line where i == j
                    break
                fi
            done
        done

        # Strip the ! from the version if present as we don't need it anymore
        version="${version//\!}"
        add_version_to_file "$origin_path" "$version" "$supported"
    done
}

declare -a runtime_to_files=()
for jdk_runtime in "${ARTIFACT_DIR_NAME}/"*; do
    runtime=$(basename "$jdk_runtime")
    files=$(find "${ARTIFACT_DIR_NAME}/$runtime" -maxdepth 1 -type f | sed "s/${ARTIFACT_DIR_NAME}\/$runtime\///g" | sort)
    runtime_to_files[$runtime]=$files
done

printf "runtime_to_files:\n"
for key in "${!runtime_to_files[@]}"; do
    printf '%s: %s\n' "$key" "${runtime_to_files[$key]}"
done

any_files_found=false
for files in "${runtime_to_files[@]}"; do
    if [[ -n "$files" ]]; then
        any_files_found=true
        break
    fi
done
if ! $any_files_found; then
    echo "No files were found so nothing to update, returning"
    exit 0
fi

files_names=("${runtime_to_files[@]}")
first_files=("${files_names[0]}")
for files in "${runtime_to_files[@]}"; do
    if [[ "$files" != "${first_files[*]}" ]]; then
        echo "Got different files from different runtimes"
        exit 1
    fi
done

instrumentationFolders=$(find instrumentation -type d -name "tested_versions" -not -path "*/build/*" | sed 's/instrumentation\///g' | sed 's/\/src\/main\/resources\/tested_versions//' | sort)

for instrumentation_name in "${first_files[@]}"; do
    origin_path=""
    for instrumentation_folder in "${instrumentationFolders[@]}"; do
        if [[ "$instrumentation_folder" == *"${instrumentation_name}"* ]]; then
            origin_path=instrumentation/"${instrumentation_folder}"/src/main/resources/tested_versions/"$instrumentation_name"
            break
        fi
    done

    handle_dependency "$instrumentation_name" "${origin_path}" "${!runtime_to_files[@]}"
done
