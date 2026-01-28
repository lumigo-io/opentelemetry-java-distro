#!/bin/sh

echo "=== ECS Metadata Endpoint Test ==="
echo "Timestamp: $(date)"
echo "ECS_CONTAINER_METADATA_URI_V4: ${ECS_CONTAINER_METADATA_URI_V4}"
echo "AWS_EXECUTION_ENV: ${AWS_EXECUTION_ENV}"
echo ""

if [ -z "$ECS_CONTAINER_METADATA_URI_V4" ]; then
    echo "ERROR: ECS_CONTAINER_METADATA_URI_V4 not set!"
    echo "This container is NOT running in ECS Fargate"
    exit 1
fi

echo "Testing container metadata endpoint..."
echo "URL: $ECS_CONTAINER_METADATA_URI_V4"
echo ""

if curl -v -s --max-time 5 "$ECS_CONTAINER_METADATA_URI_V4" 2>&1 | tee /tmp/container-metadata.log; then
    echo ""
    echo "=== Container Metadata Response ==="
    cat /tmp/container-metadata.log
    echo ""
fi

echo ""
echo "Testing task metadata endpoint..."
echo "URL: $ECS_CONTAINER_METADATA_URI_V4/task"
echo ""

if curl -v -s --max-time 5 "$ECS_CONTAINER_METADATA_URI_V4/task" 2>&1 | tee /tmp/task-metadata.log; then
    echo ""
    echo "=== Task Metadata Response ==="
    cat /tmp/task-metadata.log
    echo ""
fi

echo ""
echo "=== Test Complete ==="
echo "Container will now sleep to keep logs available..."
sleep 300
