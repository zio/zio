"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
exports.__esModule = true;
var fs = __importStar(require("fs"));
function zioEcosystemPlugin(context, options) {
    return {
        name: 'zio-ecosystem-plugin',
        extendCli: function (cli) {
            cli.command('generate-sidebar')
                .description("This command generates a sidebar file for the ZIO ecosystem")
                .action(function () {
                zioProjects().forEach(function (project) {
                    copy("node_modules/@zio.dev/".concat(project), "./docs/".concat(project));
                });
                var result = zioProjects()
                    .map(function (project) {
                    console.log(project);
                    var r = require("@zio.dev/".concat(project, "/sidebars"))
                        .sidebar
                        .map(function (c) { return mapConfig(c, "".concat(project, "/")); });
                    return r;
                });
                var content = "const sidebar = ".concat(JSON.stringify(result, null, '  '), "\nmodule.exports = sidebar");
                fs.writeFile('ecosystem-sidebar.js', content, function (err) {
                    if (err) {
                        console.error(err);
                    }
                });
            });
        }
    };
}
function zioProjects() {
    return Object
        .entries((JSON.parse(fs.readFileSync('package.json', 'utf8'))).dependencies)
        .filter(function (_a) {
        var key = _a[0], _ = _a[1];
        return key.startsWith("@zio.dev");
    })
        .map(function (_a) {
        var key = _a[0], _ = _a[1];
        return key.split("/")[1];
    })
        .sort();
}
function copy(srcDir, destDir) {
    var fs = require('fs-extra');
    try {
        fs.copySync(srcDir, destDir, { overwrite: true || false });
        console.log('success!');
    }
    catch (err) {
        console.error(err);
    }
}
function isSidebarItemCategoryConfig(item) {
    return item.items !== undefined;
}
function mapConfig(config, prefix) {
    if (typeof (config) === "string") {
        return prefix + config;
    }
    else if (isSidebarItemCategoryConfig(config)) {
        return __assign(__assign({}, config), { link: config.link ? __assign(__assign({}, config.link), { 'id': prefix + config.link.id }) : undefined, items: config.items
                .map(function (item) { return mapConfig(item, prefix); }) });
    }
}
exports["default"] = zioEcosystemPlugin;
