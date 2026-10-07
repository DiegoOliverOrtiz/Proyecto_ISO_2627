/**
 * The set of internal Stylelint-based rules
 */
declare const plugins: {
    ruleName: string;
    rule: import("stylelint").Rule<any, any>;
}[];
export { plugins };
