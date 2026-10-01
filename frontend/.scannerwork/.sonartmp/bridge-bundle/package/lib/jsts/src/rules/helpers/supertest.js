"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Supertest = void 0;
const _1 = require(".");
var Supertest;
(function (Supertest) {
    function isImported(context) {
        return ((0, _1.getRequireCalls)(context).some(r => r.arguments[0].type === 'Literal' && r.arguments[0].value === 'supertest') || (0, _1.getImportDeclarations)(context).some(i => i.source.value === 'supertest'));
    }
    Supertest.isImported = isImported;
    function isAssertion(context, node) {
        const fqn = extractFQNForCallExpression(context, node);
        if (!fqn) {
            return false;
        }
        const names = fqn.split('.');
        /**
         * supertest assertions look like `[supertest instance](...).[HTTP verb](...).expect(...)`, typically:
         * `supertest(application).get('/foo').expect(200)`
         * hence only the first and third values matter, the second one being an HTTP verb irrelevant for assertion detection
         */
        return names.length >= 3 && names[0] === 'supertest' && names[2] === 'expect';
    }
    Supertest.isAssertion = isAssertion;
    function extractFQNForCallExpression(context, node) {
        if (node.type !== 'CallExpression') {
            return undefined;
        }
        return (0, _1.getFullyQualifiedName)(context, node);
    }
})(Supertest || (exports.Supertest = Supertest = {}));
//# sourceMappingURL=supertest.js.map