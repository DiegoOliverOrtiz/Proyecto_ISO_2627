import { Rule } from 'eslint';
import * as estree from 'estree';
export declare namespace Supertest {
    function isImported(context: Rule.RuleContext): boolean;
    function isAssertion(context: Rule.RuleContext, node: estree.Node): boolean;
}
