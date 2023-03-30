import {copyFileSync, existsSync} from 'fs';
import {dirname, join} from 'path';
import {Duration, Stack, StackProps} from 'aws-cdk-lib';
import {SubnetType, Vpc} from 'aws-cdk-lib/aws-ec2';
import {Platform} from 'aws-cdk-lib/aws-ecr-assets';
import {
  AwsLogDriver,
  Cluster,
  ContainerImage,
  FargateTaskDefinition,
  Protocol as EcsProtocol,
  Secret as EcsSecret
} from 'aws-cdk-lib/aws-ecs';
import {ApplicationLoadBalancedFargateService} from 'aws-cdk-lib/aws-ecs-patterns';
import {Secret} from 'aws-cdk-lib/aws-secretsmanager';
import {Construct} from 'constructs';
import {ApplicationProtocol, Protocol} from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import {LogGroup} from "aws-cdk-lib/aws-logs";

export class EcsFargateElbStack extends Stack {

  constructor(scope: Construct, id: string, props: StackProps) {
    super(scope, id, props);

    const suffix = props.tags?.['lumigo:suffix'] || '';

    const lumigoTokenSecret = EcsSecret.fromSecretsManager(
        Secret.fromSecretNameV2(this, 'Secret', 'AccessKeys'),
        'LumigoToken'
    );

    const vpc = new Vpc(this, 'JavaagentFargateTestVpc' + suffix, {
      cidr: '10.0.0.0/16',
      maxAzs: 3, // Default is all AZs in region
      natGateways: 1,
      subnetConfiguration: [
        {
          name: 'private-subnet',
          subnetType: SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
        {
          name: 'public-subnet',
          subnetType: SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
    });

    const cluster = new Cluster(this, 'JavaagentFargateTestCluster' + suffix, {
      vpc: vpc,
    });

    const jar_path = join(dirname(dirname(dirname(dirname(__dirname)))), 'agent', 'build', 'libs', 'agent-dev-SNAPSHOT-all.jar')

    // fail if jar is not found
    if (!existsSync(jar_path)) {
      throw new Error('jar not found at ' + jar_path)
    }

    copyFileSync(
        jar_path,
        join(__dirname, 'containers', 'server', 'distro', 'lumigo-opentelemetry-distro.jar')
    )

    const serverPort = 8080;

    const logGroup = new LogGroup(this, 'JavaagentFargateTestLogGroup' + suffix, {
      logGroupName: 'JavaagentFargateTestLogGroup' + suffix,
    });
    const logDriver = new AwsLogDriver({
      streamPrefix: 'springboot',
      logGroup: logGroup,
    });

    const javaCrawlerTaskDefinition = new FargateTaskDefinition(this, 'TaskDef');
    javaCrawlerTaskDefinition.addContainer('app', {
      image: ContainerImage.fromAsset(join(__dirname, 'containers', 'server'), {
        platform: Platform.LINUX_AMD64,
      }),
      memoryReservationMiB: 256,
      environment: {
        AWS_REGION: props.env?.region || 'unknown_region',
        SERVER_PORT: String(serverPort),
        LUMIGO_DEBUG_SPANDUMP: '/dev/stdout'
      },
      secrets: {
        LUMIGO_TRACER_TOKEN: lumigoTokenSecret,
      },
      portMappings: [
        {
          containerPort: serverPort,
          protocol: EcsProtocol.TCP,
        },
      ],
      logging: logDriver,
    });

    const serverService = new ApplicationLoadBalancedFargateService(this, 'SpringBoot', {
      cluster,
      taskDefinition: javaCrawlerTaskDefinition,
      desiredCount: 1,
      targetProtocol: ApplicationProtocol.HTTP,
      listenerPort: serverPort,
    });
    serverService.targetGroup.configureHealthCheck({
      path: '/greeting',
      interval: Duration.seconds(60),
      unhealthyThresholdCount: 10,
      port: String(serverPort),
      protocol: Protocol.HTTP,
    });
  }

}
