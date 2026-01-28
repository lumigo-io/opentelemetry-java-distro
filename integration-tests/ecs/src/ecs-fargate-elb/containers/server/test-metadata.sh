#!/bin/bash
set -e

echo "=== Testing ECS Metadata Endpoint ==="
echo "ECS_CONTAINER_METADATA_URI_V4: $ECS_CONTAINER_METADATA_URI_V4"

if [ -z "$ECS_CONTAINER_METADATA_URI_V4" ]; then
    echo "ERROR: ECS_CONTAINER_METADATA_URI_V4 not set!"
else
    echo "Testing container metadata endpoint..."
    if curl -s --max-time 5 "$ECS_CONTAINER_METADATA_URI_V4" > /tmp/container-metadata.json; then
        echo "SUCCESS: Container metadata endpoint reachable"
        echo "Response preview:"
        cat /tmp/container-metadata.json | head -c 500
        echo ""
    else
        echo "ERROR: Failed to reach container metadata endpoint"
        echo "curl exit code: $?"
    fi

    echo ""
    echo "Testing task metadata endpoint..."
    if curl -s --max-time 5 "$ECS_CONTAINER_METADATA_URI_V4/task" > /tmp/task-metadata.json; then
        echo "SUCCESS: Task metadata endpoint reachable"
        echo "Response preview:"
        cat /tmp/task-metadata.json | head -c 500
        echo ""
    else
        echo "ERROR: Failed to reach task metadata endpoint"
        echo "curl exit code: $?"
    fi
fi

echo "=== End Metadata Endpoint Test ==="
echo ""

# Continue with original entrypoint
exec "$@"
