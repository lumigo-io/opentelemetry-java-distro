import { App } from 'aws-cdk-lib';
import { EcsFargateElbStack } from './ecs-fargate-elb/ecs-fargate-elb';

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
};

const app = new App();

new EcsFargateElbStack(app, 'lumigo-java-distro-itests-dev', { env });

app.synth();