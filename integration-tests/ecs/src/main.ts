import { App } from 'aws-cdk-lib';
import { EcsFargateElbStack } from './ecs-fargate-elb/ecs-fargate-elb';

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION,
};

// Allow to deploy multiple stacks in the same account
let suffix = '-' + (process.env.DEPLOYMENT_SUFFIX || 'dev');

const app = new App();

new EcsFargateElbStack(app, 'lumigo-java-distro-itests'+ suffix, { env, tags: { 'lumigo:suffix': suffix } });

app.synth();
