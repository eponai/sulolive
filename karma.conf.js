// Karma configuration

var path = require('path');

module.exports = function(config) {
  
  config.set({
    // frameworks to use
    // available frameworks: https://npmjs.org/browse/keyword/karma-adapter
    frameworks: [ 'cljsTest'],

    // list of files / patterns to load in the browser
    files: [
      'resources/private/test-js/test-render-context.js',
      'resources/public/test/js/out/budget.js',
      // Using included false for all javascript because budget
      // will include them in it's <script> tag.
      // Enables us to use :optimizations :none for tests.
      {pattern: 'resources/public/test/js/out/**/*.js', included: false}
    ],


    // list of files to exclude
    exclude: [

    ],

    // test results reporter to use
    // possible values: 'dots', 'progress', or 'junit'?
    // available reporters: https://npmjs.org/browse/keyword/karma-reporter
    reporters: ['progress','junit'],

    junitReporter: {
      outputDir:  process.env.CIRCLE_TEST_REPORTS || 'test-reports',
      outputFile: 'budget-cljs-tests.xml',
      suite: 'budget',
    },


    // web server port
    port: 9876,


    // enable / disable colors in the output (reporters and logs)
    colors: true,


    // level of logging
    // possible values: config.LOG_DISABLE || config.LOG_ERROR || config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
    logLevel: config.LOG_INFO,


    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: false,


    // start these browsers
    // available browser launchers: https://npmjs.org/browse/keyword/karma-launcher
    browsers: ['Chrome'],


    // Continuous Integration mode
    // if true, Karma captures browsers, runs the tests and exits
    singleRun: false
  });
};
