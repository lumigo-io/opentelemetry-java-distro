import {join} from 'path';
import {Duration, Stack, StackProps} from 'aws-cdk-lib';
import {IpAddresses, SubnetType, Vpc} from 'aws-cdk-lib/aws-ec2';
import {Platform} from 'aws-cdk-lib/aws-ecr-assets';
import {
  AwsLogDriver,
  Cluster,
  ContainerImage,
  FargateTaskDefinition,
} from 'aws-cdk-lib/aws-ecs';
import {Construct} from 'constructs';
import {LogGroup} from "aws-cdk-lib/aws-logs";

export class MetadataTestStack extends Stack {

  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);

    const suffix = props.tags?.['lumigo:suffix'] || '-metadata-test';

    const vpc = new Vpc(this, 'MetadataTestVpc' + suffix, {
      ipAddresses: IpAddresses.cidr('10.1.0.0/16'),
      maxAzs: 2,
      natGateways: 0,  // No NAT needed, just for testing
      subnetConfiguration: [
        {
          name: 'public-subnet',
          subnetType: SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
    });

    const cluster = new Cluster(this, 'MetadataTestCluster' + suffix, {
      vpc: vpc,
    });

    const logGroup = new LogGroup(this, 'MetadataTestLogGroup' + suffix, {
      logGroupName: 'MetadataTestLogGroup' + suffix,
    });

    const logDriver = new AwsLogDriver({
      streamPrefix: 'metadata-test',
      logGroup: logGroup,
    });

    const taskDefinition = new FargateTaskDefinition(this, 'MetadataTestTaskDef');
    taskDefinition.addContainer('test', {
      image: ContainerImage.fromAsset(join(__dirname, 'metadata-test'), {
        platform: Platform.LINUX_AMD64,
      }),
      logging: logDriver,
    });

    // Note: We're not creating a service, just the task definition
    // You can run this manually with:
    // aws ecs run-task --cluster <cluster-name> --task-definition <task-def-arn> --launch-type FARGATE --network-configuration ...
  }
}
