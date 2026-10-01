"use strict";
/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
// https://sonarsource.github.io/rspec/#/rspec/S6959/javascript
Object.defineProperty(exports, "__esModule", { value: true });
exports.rule = void 0;
const helpers_1 = require("../helpers");
exports.rule = {
    meta: {
        messages: {
            message: 'Add an initial value to this "reduce()" call.',
        },
    },
    create(context) {
        const services = context.parserServices;
        function isArray(node) {
            if ((0, helpers_1.isRequiredParserServices)(services)) {
                return (0, helpers_1.isArray)(node, services);
            }
            else {
                return (0, helpers_1.isArrayExpression)((0, helpers_1.getUniqueWriteUsageOrNode)(context, node));
            }
        }
        return {
            CallExpression(node) {
                if ((0, helpers_1.isCallingMethod)(node, 1, 'reduce') && isArray(node.callee.object)) {
                    context.report({
                        node: node.callee.property,
                        messageId: 'message',
                    });
                }
            },
        };
    },
};
//# sourceMappingURL=rule.js.map