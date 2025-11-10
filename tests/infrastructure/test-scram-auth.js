const { Kafka, logLevel } = require('kafkajs');
const fs = require('fs');
const path = require('path');

// Configuration
const KAFKA_BROKER = 'localhost:57005';
const USERNAME = process.argv[2] || 'demo-scram-user';
const PASSWORD = process.argv[3] || 'ScramTest123!';

console.log('='.repeat(70));
console.log('SCRAM-SHA-256 Authentication Test with KafkaJS');
console.log('='.repeat(70));
console.log(`Broker:    ${KAFKA_BROKER}`);
console.log(`Username:  ${USERNAME}`);
console.log(`Password:  ${PASSWORD}`);
console.log(`Mechanism: SCRAM-SHA-256`);
console.log(`SSL:       Enabled`);
console.log('='.repeat(70));

const kafka = new Kafka({
  clientId: 'scram-test-client',
  brokers: [KAFKA_BROKER],
  ssl: {
    rejectUnauthorized: false,
    ca: [fs.readFileSync(path.join(__dirname, 'certs/ca-root.pem'))],
  },
  sasl: {
    mechanism: 'scram-sha-256',
    username: USERNAME,
    password: PASSWORD,
  },
  logLevel: logLevel.INFO,
});

async function testAuthentication() {
  const admin = kafka.admin();

  try {
    console.log('\n📡 Connecting to Kafka broker...');
    await admin.connect();
    console.log('✅ Connection successful!\n');

    console.log('🔍 Fetching cluster information...');
    const cluster = await admin.describeCluster();

    console.log('\n' + '='.repeat(70));
    console.log('✅✅✅ AUTHENTICATION SUCCESSFUL! ✅✅✅');
    console.log('='.repeat(70));
    console.log(`Cluster ID:     ${cluster.clusterId}`);
    console.log(`Controller:     ${cluster.controller}`);
    console.log(`Brokers:        ${cluster.brokers.length}`);
    cluster.brokers.forEach(broker => {
      console.log(`  - Broker ${broker.nodeId}: ${broker.host}:${broker.port}`);
    });

    console.log('\n🔍 Listing topics...');
    const topics = await admin.listTopics();
    console.log(`Found ${topics.length} topics:`);
    topics.slice(0, 10).forEach(topic => {
      console.log(`  - ${topic}`);
    });
    if (topics.length > 10) {
      console.log(`  ... and ${topics.length - 10} more`);
    }

    console.log('\n🎉 All tests passed! SCRAM-SHA-256 authentication is working correctly.');
    console.log('='.repeat(70));

  } catch (error) {
    console.error('\n' + '='.repeat(70));
    console.error('❌ AUTHENTICATION FAILED');
    console.error('='.repeat(70));
    console.error(`Error: ${error.message}`);

    if (error.message.includes('invalid credentials')) {
      console.error('\n💡 Possible causes:');
      console.error('  1. Password not synced to Kafka yet');
      console.error('  2. Wrong password provided');
      console.error('  3. User not created in Kafka');
      console.error('\n🔧 Try running reconciliation:');
      console.error('  curl -X POST http://localhost:57010/api/reconcile/trigger');
    }

    console.error('='.repeat(70));
    process.exit(1);

  } finally {
    await admin.disconnect();
  }
}

testAuthentication();
