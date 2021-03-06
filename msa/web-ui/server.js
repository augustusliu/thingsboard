const config = require('config'),
      logger = require('./config/logger')('main'),
      express = require('express'),
      compression = require('compression'),
      http = require('http'),
      httpProxy = require('http-proxy'),
      path = require('path'),
      historyApiFallback = require("connect-history-api-fallback");

var server;

(async() => {
    try {
        logger.info('Starting ThingsBoard Web UI Microservice...');

        const bindAddress = config.get('server.address');
        const bindPort = config.get('server.port');

        const thingsboardEnableProxy = config.get('thingsboard.enableProxy');

        const thingsboardHost = config.get('thingsboard.host');
        const thingsboardPort = config.get('thingsboard.port');

        logger.info('Bind address: %s', bindAddress);
        logger.info('Bind port: %s', bindPort);
        logger.info('ThingsBoard Enable Proxy: %s', thingsboardEnableProxy);
        logger.info('ThingsBoard host: %s', thingsboardHost);
        logger.info('ThingsBoard port: %s', thingsboardPort);

        const useApiProxy = thingsboardEnableProxy === "true";

        var webDir = path.join(__dirname, 'web');

        if (typeof process.env.WEB_FOLDER === 'string') {
            webDir = path.resolve(process.env.WEB_FOLDER);
        }
        logger.info('Web folder: %s', webDir);

        const app = express();
        server = http.createServer(app);

        var apiProxy;
        if (useApiProxy) {
            apiProxy = httpProxy.createProxyServer({
                target: {
                    host: thingsboardHost,
                    port: thingsboardPort
                }
            });

            apiProxy.on('error', function (err, req, res) {
                logger.warn('API proxy error: %s', err.message);
                if (res.writeHead) {
                  res.writeHead(500);
                  if (err.code && err.code === 'ECONNREFUSED') {
                    res.end('Unable to connect to ThingsBoard server.');
                  } else {
                    res.end('Thingsboard server connection error: ' + err.code ? err.code : '');
                  }
                }
            });
            app.all('/api/*', (req, res) => {
              logger.debug(req.method + ' ' + req.originalUrl);
              apiProxy.web(req, res);
            });

            app.all('/static/rulenode/*', (req, res) => {
              apiProxy.web(req, res);
            });

            server.on('upgrade', (req, socket, head) => {
              apiProxy.ws(req, socket, head);
            });
        }

        app.use(historyApiFallback());
        app.use(compression());

        const root = path.join(webDir, 'public');

        app.use(express.static(root));

        server.listen(bindPort, bindAddress, (error) => {
            if (error) {
                logger.error('Failed to start ThingsBoard Web UI Microservice: %s', e.message);
                logger.error(error.stack);
                exit(-1);
            } else {
                logger.info('==> ????  Listening on port %s.', bindPort);
                logger.info('Started ThingsBoard Web UI Microservice.');
            }
        });

    } catch (e) {
        logger.error('Failed to start ThingsBoard Web UI Microservice: %s', e.message);
        logger.error(e.stack);
        exit(-1);
    }
})();

process.on('exit', function () {
    exit(0);
});

function exit(status) {
    logger.info('Exiting with status: %d ...', status);
    if (server) {
        logger.info('Stopping HTTP Server...');
        var _server = server;
        server = null;
        _server.close(() => {
            logger.info('HTTP Server stopped.');
            process.exit(status);
        });
    } else {
        process.exit(status);
    }
}
