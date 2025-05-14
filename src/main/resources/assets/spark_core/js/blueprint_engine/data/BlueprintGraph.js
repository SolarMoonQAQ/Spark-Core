// BlueprintGraph.js
// 蓝图图结构定义

/**
 * @typedef {Object} BlueprintGraph
 * @property {string} id - 唯一标识
 * @property {string} name - 图名称
 * @property {BlueprintNode[]} nodes - 节点数组
 * @property {BlueprintConnection[]} connections - 连接数组
 * @property {BlueprintVariable[]} variables - 变量数组
 * @property {Object} metadata - 附加元数据
 */

export class BlueprintGraph {
  /**
   * @param {Object} opts
   * @param {string} opts.id
   * @param {string} opts.name
   * @param {BlueprintNode[]} opts.nodes
   * @param {BlueprintConnection[]} opts.connections
   * @param {BlueprintVariable[]} opts.variables
   * @param {Object} opts.metadata
   */
  constructor({ id = '', name = '', nodes = [], connections = [], variables = [], metadata = {} } = {}) {
    this.id = id;
    this.name = name;
    this.nodes = nodes;
    this.connections = connections;
    this.variables = variables;
    this.metadata = metadata;
  }
}
