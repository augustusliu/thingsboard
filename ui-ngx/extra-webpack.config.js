
const CompressionPlugin = require("compression-webpack-plugin");
const TerserPlugin = require("terser-webpack-plugin");
const webpack = require("webpack");
const dirTree = require("directory-tree");
const AngularCompilerPlugin = require('@ngtools/webpack');

var langs = [];

dirTree("./src/assets/locale/", {extensions: /\.json$/}, (item) => {
  /* It is expected what the name of a locale file has the following format: */
  /* 'locale.constant-LANG_CODE[_REGION_CODE].json', e.g. locale.constant-es.json or locale.constant-zh_CN.json*/
  langs.push(item.name.slice(item.name.lastIndexOf("-") + 1, -5));
});

module.exports = (config, options) => {
  config.plugins.push(
    new webpack.DefinePlugin({
      TB_VERSION: JSON.stringify(require("./package.json").version),
      SUPPORTED_LANGS: JSON.stringify(langs),
    })
  );
  config.plugins.push(
    new webpack.ProvidePlugin(
      {
        $: "jquery"
      }
    )
  );
  config.plugins.push(
    new CompressionPlugin({
      filename: "[path][base].gz[query]",
      algorithm: "gzip",
      test: /\.js$|\.css$|\.html$|\.svg?.+$|\.jpg$|\.ttf?.+$|\.woff?.+$|\.eot?.+$|\.json$/,
      threshold: 10240,
      minRatio: 0.8,
      deleteOriginalAssets: false,
    })
  );
  config.plugins.push(
    new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/)
  );

  if (config.mode === 'production') {
    const index = config.plugins.findIndex(p => p instanceof AngularCompilerPlugin.AngularCompilerPlugin);
    const angularCompilerOptions = config.plugins[index]._options;
    angularCompilerOptions.emitClassMetadata = true;
    angularCompilerOptions.emitNgModuleScope = true;
    config.plugins.splice(index, 1);
    config.plugins.push(new AngularCompilerPlugin.AngularCompilerPlugin(angularCompilerOptions));
    const terserPluginOptions = config.optimization.minimizer[1].options;
    delete terserPluginOptions.terserOptions.compress.global_defs.ngJitMode;
    terserPluginOptions.terserOptions.compress.side_effects = false;
    config.optimization.minimizer.splice(1, 1);
    config.optimization.minimizer.push(new TerserPlugin(terserPluginOptions));
  }
  return config;
};
