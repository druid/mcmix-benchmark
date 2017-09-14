/**
 * Copyright 2017 Cybernetica AS
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
     * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
var util = require('util');
var jswcp = require('./jswcp.node'); // Sharemind Web Controller Proxy

function ulog(str) {
  if (str)
    util.log(str);
}
function uerr(str) {
  util.error('ERROR: ' + str);
}

function udebug(str) {
  util.debug(str);
}

function usage() {
  util.log('Usage: nodejs proxy.js <clusterID> <serverID (1-3)>');
}

// Parse command-line arguments:
if (process.argv.length < 4) {
  usage();
  process.exit(1);
}

var clusterId = parseInt(process.argv[2]);
var serverId = parseInt(process.argv[3]);

var serverPort = '3000' + clusterId;
var proxyPort = '800' + clusterId;
var proxyHost = '0.0.0.0';

var nextProxy = 'http://sharemind' + (serverId == 3 ? 1 : serverId+1) + '.cluster4:800' + clusterId;

var nrOfClusters = 2;

// Number of users is (nrOfUsers * nrOfClusters)
// var nrOfUsers = new Buffer([0x10, 0x27]); // 10k
// var nrOfUsers = new Buffer([0xa0, 0x86, 0x01]); // 100k
// var nrOfUsers = new Buffer([0x40, 0x42, 0x0f]); // 1M
var nrOfUsers = new Buffer([0x50, 0xc3]); // 50k
// var nrOfUsers = new Buffer([0xb8, 0x88]); // 35k
// var nrOfUsers = new Buffer([0x98, 0x1c, 0x05]); // 335k
// var nrOfUsers = new Buffer([0x20, 0xa1, 0x07]); // 500k
// var nrOfUsers = new Buffer([0xa8, 0x61]); // 25k
// var nrOfUsers = new Buffer([0x90, 0xd0, 0x03]); // 250k
// var nrOfUsers = new Buffer([0x48, 0xe8, 0x01]); // 125k
// var nrOfUsers = new Buffer([0x10, 0x98, 0x02]); // 170k
// var nrOfUsers = new Buffer([0x68, 0x42]); // 17k
// var nrOfUsers = new Buffer([0xd4, 0x30]); // 12,500

// Message size is (mSize * 8 bytes)
var mSize = new Buffer([0x01]); // 1

var clusterProxyHosts = [];
var clusterProxies = [];
for (var i = 0; i < nrOfClusters; i++) {
  clusterProxyHosts[i] = 'http://sharemind' + serverId + '.cluster:800' + (i+1);
}

// --

if (clusterId > nrOfClusters) {
  ulog('Nothing to do');
  process.exit(0);
}

// --
var beginTime = 0;
var clusterInUse = false;
var proxyData = [];
var proxyStatus = [0, 0, 0];
var nextStep = 1;

function changeProxyStatus(proxyId, status) {
  isChange = false;
  for (var i = 0; i < status.length; ++i) {
    if (status[i] > proxyStatus[i]) {
      proxyStatus[i] = status[i];
      isChange = true;
    }
  }

  return isChange;
}

// Set up JSWCP global stuff:
try {
  global.wcpGlobals = new jswcp.JsWebControllerGlobals();
  ulog('Network globals initialized.');
} catch (err) {
  uerr(err);
  process.exit(2);
}
// Create logger object
try {
  global.logger = new jswcp.JsWebControllerLogger(function (msg) {
    ulog(msg);
  });
  ulog("JSWCP logger initialized.");
} catch (err) {
  uerr(err);
  process.exit(2);
}

global.wcp = null;
try {
  // Create the web controller object
  global.wcp = new jswcp.JsWebControllerProxy(global.logger);

  ulog("Sharemind proxy controller initialized.");
} catch (err) {
  uerr(err);
  process.exit(2);
}

// Set up proxy server
function requestHandler(req, res) {
  res.writeHead(200);
  res.end('');
}
var http    = require('http');
var server  = http.createServer(requestHandler);

var io = require('socket.io')(server);
server.listen(proxyPort, proxyHost);
ulog('Proxy listening on hostname: "' + proxyHost + '", port: "' + proxyPort + '"');

// Process incoming socket.io connections
io.on('connection', function (socket) {
  udebug('New client connected: ' + socket.id);

  socket.on('disconnect', function () {
    udebug('Client disconnected: ' + socket.id);
  });

  socket.on('message', function (data) {
    var messageCopy = data;
    if (messageCopy.a) messageCopy.a = [];
    udebug('Received message: ' + JSON.stringify(messageCopy));

    if (messageCopy.ping) return; // Ignore ping

    // Message from the same cluster/island:
    if (data.clusterId == clusterId) {
      if (changeProxyStatus(data.serverId, data.status)) {
        client.send({clusterId: clusterId, serverId: serverId, status: proxyStatus});

        // Take step 1:
        if (proxyStatus.every(function (e) { return e >= 1; }) && nextStep == 1) {
          udebug('Everybody has status 1 now!');
          nextStep = 2;

          // Time begins here:
          beginTime = process.hrtime();

          // Connect to Sharemind:
          clusterInUse = true;
          try {
            global.wcp.Connect('proxy' + clusterId + '' + serverId + '.cfg', 1234567, // TODO: Use random nonce
              function (data) {

                if (data instanceof Error) {
                  uerr('Connecting to Sharemind server failed.');
                  process.exit(1);
                }

                ulog('Running step 1 on Sharemind now');
                ulog(data);

                // Start first step of the process:
                var params = {
                  'nrOfUsers': {'type': 'uint64', 'typesize': 8, 'value': nrOfUsers},
                  'mSize': {'type': 'uint64', 'typesize': 8, 'value': mSize}
                };
                global.wcp.RunCode('conversation-step1.sb', 
                  params, function (data) {

                    if (data instanceof Error) {
                      uerr('Failed to run script.');
                      process.exit(1);
                    }

                    ulog('Finished running step 1');
                    //udebug(JSON.stringify(data));

                    global.wcp.Disconnect(function (data) {
                      if (data instanceof Error) {
                        uerr('Failed to disconnect from Sharemind');
                        process.exit(1);
                      }
                      udebug('Disconnected from Sharemind');
                      clusterInUse = false;
                    });

                    // Save my relevant part locally:
                    proxyData[clusterId-1] = data.a;

                    // Send part of result to proxies on other islands/clusters:
                    // For simplicity, we send out whole data to each other island.
                    // In real application, we would have to split it between the islands.
                    for (var i = 0; i < nrOfClusters; ++i) {
                      if (i+1 == clusterId) continue;

                      clusterProxies[i].send({clusterId: clusterId, serverId: serverId, step: 1, a: data.a});
                    }

                  });
              });
          } catch(err) {
            uerr('Failed to connect to Sharemind server.');
          }

        }
        // Take step 2:
        else if (proxyStatus.every(function (e) { return e >= 2; }) && nextStep == 2) {
          udebug('Everybody has status 2 now!');
          nextStep = 3;

          // Connect to Sharemind:
          try {
            clusterInUse = true;
            global.wcp.Connect('proxy' + clusterId + '' + serverId + '.cfg', 123456789, // TODO: Use random nonce
              function (data) {

                if (data instanceof Error) {
                  uerr('Connecting to Sharemind server failed.');
                  process.exit(1);
                }

                ulog('Running step 2 on Sharemind now');
                ulog(data);

                var params = {
                  'nrOfUsers': {'type': 'uint64', 'typesize': 8, 'value': nrOfUsers},
                  'mSize': {'type': 'uint64', 'typesize': 8, 'value': mSize},
                  a: proxyData[clusterId-1]
                };
                params.a.typesize = 8;
                udebug(params);
                proxyData = [];
                global.wcp.RunCode('conversation-step2.sb', 
                  params, function (data) {

                    if (data instanceof Error) {
                      uerr('Failed to run script.');
                      process.exit(1);
                    }

                    ulog('Finished running step 2');
                    //udebug(JSON.stringify(data));

                    global.wcp.Disconnect(function (data) {
                      if (data instanceof Error) {
                        uerr('Failed to disconnect from Sharemind');
                        process.exit(1);
                      }
                      udebug('Disconnected from Sharemind');
                      clusterInUse = false;
                    });

                    // Save my relevant part locally:
                    proxyData[clusterId-1] = data.a;

                    // Send part of result to proxies on other islands/clusters:
                    for (var i = 0; i < nrOfClusters; ++i) {
                      if (i+1 == clusterId) continue;

                      clusterProxies[i].send({clusterId: clusterId, serverId: serverId, step: 2, a: data.a});
                    }
                  });
              });
          } catch (e) {
            uerr('Failed to connect to Sharemind server.');
            process.exit(1);
          }
        }
        // Take step 3:
        else if (proxyStatus.every(function (e) { return e >= 3; }) && nextStep == 3) {
          udebug('Everybody has status 3 now!');
          nextStep = 4;

          // Connect to Sharemind:
          try {
            clusterInUse = true;
            global.wcp.Connect('proxy' + clusterId + '' + serverId + '.cfg', 12345678900, // TODO: Use random nonce
              function (data) {

                if (data instanceof Error) {
                  uerr('Connecting to Sharemind server failed.');
                  process.exit(1);
                }

                ulog('Running step 3 on Sharemind now');
                ulog(data);

                var params = {
                  'nrOfUsers': {'type': 'uint64', 'typesize': 8, 'value': nrOfUsers},
                  'mSize': {'type': 'uint64', 'typesize': 8, 'value': mSize},
                  a: proxyData[clusterId-1]
                };
                params.a.typesize = 8;
                udebug(params);
                proxyData = [];
                global.wcp.RunCode('conversation-step3.sb', 
                  params, function (data) {

                    if (data instanceof Error) {
                      uerr('Failed to run script.');
                      process.exit(1);
                    }

                    ulog('Finished running step 3');
                    //udebug(JSON.stringify(data));

                    global.wcp.Disconnect(function (data) {
                      if (data instanceof Error) {
                        uerr('Failed to disconnect from Sharemind');
                        process.exit(1);
                      }
                      udebug('Disconnected from Sharemind');
                      clusterInUse = false;

                      // Notify other islands/clusters:
                      for (var i = 0; i < nrOfClusters; ++i) {
                        if (i+1 == clusterId) continue;

                        clusterProxies[i].send({clusterId: clusterId, serverId: serverId, step: 3});
                      }

                      ulog('All done!');

                      // Time ends here:
                      var diff = process.hrtime(beginTime);
                      ulog('Time (microseconds): ' + Math.round(diff[0] * 1e6 + diff[1]/1000));
                      process.exit(0);
                    });

                  });
              });
          } catch (e) {
            uerr('Failed to connect to Sharemind server.');
            process.exit(1);
          }
        }
      }
    } 
    // Message from another cluster:
    else {
      udebug('Message from cluster ' + data.clusterId);

      if (data.a)
        proxyData[data.clusterId-1] = data.a;

      // Set up a timer that triggers when data has
      // been received from all islands
      var clbkWaitForMyself = function () {
        // Has everybody sent their data?
        if (proxyData.length == nrOfClusters) {
          // We're ready for the next step
          // but wait until Sharemind servers are free
          udebug('We are ready for the next step');

          var clbkNotifyOthers = function () {
            if (!clusterInUse) {
              var proxyStatusCopy = [0, 0, 0];
              proxyStatusCopy[serverId-1] = nextStep;
              udebug(proxyStatusCopy);
              client.send({clusterId: clusterId, serverId: serverId, status: proxyStatusCopy});
            } else {
              udebug('Sharemind servers in use, waiting...');
              setTimeout(function () {clbkNotifyOthers ();}, 2000);
            }
          };
          clbkNotifyOthers();
        } else {
          udebug('I am still not ready. Waiting...');
          setTimeout(clbkWaitForMyself, 2000);
        }
      };
      clbkWaitForMyself();
    }
  });
  socket.on('error', function (data) {
    uerr(data);
  });
});

// This is beginning

var proxyConnected = [];

var ioc = require('socket.io-client');
udebug('Connecting to ' + nextProxy);
var client = ioc.connect(nextProxy);
client.on('connect', function (data) {
  udebug('Connection successful');

  proxyConnected[clusterId-1] = 1;

  if (proxyConnected.length == nrOfClusters) {
    // We have to make empty copy here,
    // otherwise 2 servers might have reached 
    // next step too early.
    setTimeout(function () {
      var proxyStatusCopy = [0, 0, 0];
      proxyStatusCopy[serverId-1] = 1
      udebug(proxyStatusCopy);
      client.send({clusterId: clusterId, serverId: serverId, status: proxyStatusCopy});
    }, 5000); // Wait 5 seconds
  }
});
// client.on('connect_error', function (data) {
//   uerr('Connection unsuccessful. ' + data);
// });
client.on('reconnecting', function (data) {
  udebug('Reconnecting...');
});

for (var i = 0; i < nrOfClusters; ++i) {
  // Do not connect to itself:
  if (i+1 == clusterId) continue;

  var thisProxyId = i;
  udebug('Connecting to ' + clusterProxyHosts[i]);
  clusterProxies[i] = ioc.connect(clusterProxyHosts[i]);
  clusterProxies[i].on('connect', function (data) {
    udebug('Successfully connected to ' + clusterProxyHosts[thisProxyId]);

    proxyConnected[thisProxyId] = 1;

    if (proxyConnected.length == nrOfClusters) {
      // We have to make empty copy here,
      // otherwise 2 servers might have reached 
      // next step too early.
      setTimeout(function () {
        var proxyStatusCopy = [0, 0, 0];
        proxyStatusCopy[serverId-1] = 1
        udebug(proxyStatusCopy);
        client.send({clusterId: clusterId, serverId: serverId, status: proxyStatusCopy});
      }, 5000); // Wait 5 seconds
    }

    // Set up a ping:
    // var thisSocket = clusterProxies[i];
    // setInterval(function () {
    //   thisSocket.send({clusterId: clusterId, serverId: serverId, ping: 'ping'});
    // }, 5000);
  });
  clusterProxies[i].on('reconnecting', function (data) {
    udebug('Reconnecting...');
  });
}