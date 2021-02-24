/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
var graphBrowser, mode, colors = [], c = 0;
var nodeIds = [], relIds = [], removedRel;
var activeTabRightGraphKey = 'structrActiveTabRightGraph_' + port;
var activeTabLeftGraphKey = 'structrActiveTabLeftGraph_' + port;
var activeTabLeftGraph, activeTabRightGraph;
var queriesSlideout, displaySlideout, filtersSlideout, graph;
var savedQueriesKey = 'structrSavedQueries_' + port;
var relTypes = {}, nodeTypes = {}, color = {}, relColors = {}, hasDragged, hasDoubleClicked, clickTimeout, doubleClickTime = 250, refreshTimeout;
var filteredNodeTypes = [], hiddenNodeTypes = [], hiddenRelTypes = [];
var edgeType = 'curvedArrow';
var schemaNodes = {}, schemaRelationships = {}, schemaNodesById = {};
var displayHtmlTypes = false, displayCustomTypes = true, displayCoreTypes = false, displayUiTypes = false, displayLogTypes = false, displayOtherTypes = false;
var maxRels = 100, defaultNodeColor = '#a5a5a5', defaultRelColor = '#cccccc';
var tmpX, tmpY, nodeLabelsHidden = false, edgeLabelsHidden  = false;
var selectionTable;
var forceAtlas2Config = {
	gravity: 1,
	strongGravityMode: true,
	adjustSizes: true,
	iterationsPerRender: 10,
	barnesHutOptimize: false,
	slowDown: 2
};

var animating = false;
var timeout   = 0;
var expanded  = {};
var count     = 0;

$(document).ready(function() {

	Structr.registerModule(_Graph);

	$(document.body).on('mousedown', function(e) {
		tmpX = e.clientX;
		tmpY = e.clientY;
	});

	$(document.body).on('mouseup', function(e) {
		hasDragged = (tmpX && tmpY && (tmpX !== e.clientX || tmpY !== e.clientY));
		tmpX = e.clientX;
		tmpY = e.clientY;
	});

	$(document).on('change', '#nodeFilters input', function(e) {
		_Graph.updateNodeTypes();
		LSWrapper.setItem(_Graph.displayTypeConfigKey, _Graph.getTypeVisibilityConfig());
	});

	$(document).on('click', '.remove-cypher-parameter', function() {
		$(this).parent().remove();
	});
});

var _Graph = {
	_moduleName: 'graph',
	displayTypeConfigKey: 'structrGraphDisplayTypes_' + port,
	searchField: undefined,
	init: function() {

		// Colors created with http://paletton.com
		var palettonColors = [
			'#82CE25', '#1DA353', '#E24C29', '#C22363',
			'#B7ED74', '#61C68A', '#FF967D', '#E26F9E',
			'#9BDD4A', '#3BAF6A', '#F37052', '#D1467E',
			'#63A80F', '#0C853D', '#B93111', '#9E0E48',
			'#498500', '#00692A', '#921C00', '#7D0033',
			'#019097', '#103BA8', '#FAA800', '#FA7300',
			'#3FB0B5', '#5070C1', '#FFC857', '#FFA557',
			'#1CA2A8', '#2E55B7', '#FFB929', '#FF8C29',
			'#017277', '#0B2E85', '#C68500', '#C65B00',
			'#00595D', '#072368', '#9A6800', '#9A4700'
		];
		colors.concat(palettonColors);

		var max = 255;
		var steps = [21, 53, 31];

		for (var i = 50; i < 999; i++) {
			var col = 'rgb(' + (steps[i%3] * i) % max + ',' + (steps[(i+1)%3] * i) % max + ',' + (steps[(i+2)%3] * i) % max + ')';
			colors.push(col);
		}

		_Graph.updateNodeTypes();

		var canvasWidth = $('#graph-canvas').width();
		var canvasHeight = canvasWidth / (16 / 9);

		var editDistance = Math.sqrt(Math.pow(canvasWidth, 2) + Math.pow(canvasHeight, 2)) * 0.2;
		var graphBrowserSettings = {
			graphContainer: 'graph-canvas',
			moduleSettings: {
				'tooltips' : {
					node : {
						cssClass: 'graphBrowserTooltips',
						renderer: _Graph.renderNodeTooltip
					},
					edge : {
						cssClass: 'graphBrowserTooltips',
						renderer: _Graph.renderEdgeTooltip
					}
				},
				'nodeExpander': {
					container: 'graph-info',
					newEdgeSize: 40,
					newNodeSize: 20,
					margins: {top: 24, left: 0},
					edgeType: "curvedArrow",
					infoButtonRenderer: _Graph.renderNodeExpanderInfoButton,
					defaultInfoButtonColor: '#e5e5e5',
					expandButtonsTimeout: 1000,
					onNodesAdded: _Graph.onNodesAdded
				},
				'selectionTools': {'container': 'graph-canvas'},
				'relationshipEditor' : {
					incommingRelationsKey: 'shift',
					outgoingRelationsKey: 'ctrl',
					deleteEvent: 'doubleClickEdge',
					onDeleteRelation: undefined,
					maxDistance: editDistance
				},
				'currentNodeTypes': {},
				'nodeFilter': {}
			},
			sigmaSettings: {
				immutable: false,
				minNodeSize: 1,
				maxNodeSize: 10,
				labelThreshold: 0,
				borderSize: 4,
				defaultNodeBorderColor: '#a5a5a5',
				singleHover: true,
				doubleClickEnabled: false,
				minEdgeSize: 1,
				maxEdgeSize: 4,
				newEdgeSize: 40,
				enableEdgeHovering: true,
				edgeHoverColor: 'default',
				edgeHoverSizeRatio: 1.3,
				edgeHoverExtremities: true,
				autoCurveRatio: 1,
				autoCurveSortByDirection: true,
				defaultEdgeColor: '#cccccc',
				defaultEdgeHoverColor: '#81ce25',
				defaultEdgeLabelActiveColor: '#81ce25',
				defaultEdgeActiveColor: '#81ce25',
				defaultEdgeType: 'arrow',
				newEdgeType: 'curvedArrow',
				minArrowSize: 4,
				maxArrowSize: 8,
				labelSize: 'proportional',
				labelSizeRatio: 1,
				labelAlignment: 'right',
				nodeHaloColor: 'rgba(236, 81, 72, 0.2)',
				nodeHaloSize: 20
			},
			lassoSettings: {
				'strokeStyle': 'rgb(129, 206, 37)',
				'lineWidth': 5,
				'fillWhileDrawing': true,
				'fillStyle': 'rgba(129, 206, 37, 0.2)',
				'cursor': 'crosshair'
			}
		};

		graphBrowser = new GraphBrowser(graphBrowserSettings);
		graphBrowser.start();

		graphBrowser.bindEvent('clickStage', _Graph.handleClickStageEvent);
		graphBrowser.bindEvent('clickNode', _Graph.handleClickNodeEvent);
		graphBrowser.bindEvent('doubleClickNode', _Graph.handleDoubleClickNodeEvent);
		graphBrowser.bindEvent('drag', _Graph.handleDragNodeEvent);
		graphBrowser.bindEvent('startdrag', _Graph.handleStartDragNodeEvent);
		graphBrowser.bindEvent('clickEdge', _Graph.handleClickEdgeEvent);
	},

	onload: async function() {

		let html = await Structr.fetchHtmlTemplate('graph/graph', {});

		main[0].innerHTML = html;

		Structr.updateMainHelpLink(Structr.getDocumentationURLForTopic('graph'));

		activeTabLeftGraph = LSWrapper.getItem(activeTabRightGraphKey);
		activeTabRightGraph = LSWrapper.getItem(activeTabLeftGraphKey);


		queriesSlideout = $('#queries');
		displaySlideout = $('#display');
		filtersSlideout = $('#filters');

		var savedTypeVisibility = LSWrapper.getItem(_Graph.displayTypeConfigKey) || {};
		$('#graphTypeToggleRels').prop('checked', (savedTypeVisibility.rels === undefined ? true : savedTypeVisibility.rels));
		$('#graphTypeToggleCustom').prop('checked', (savedTypeVisibility.custom === undefined ? true : savedTypeVisibility.custom));
		$('#graphTypeToggleCore').prop('checked', (savedTypeVisibility.core === undefined ? true : savedTypeVisibility.core));
		$('#graphTypeToggleHtml').prop('checked', (savedTypeVisibility.html === undefined ? true : savedTypeVisibility.html));
		$('#graphTypeToggleUi').prop('checked', (savedTypeVisibility.ui === undefined ? true : savedTypeVisibility.ui));
		$('#graphTypeToggleLog').prop('checked', (savedTypeVisibility.log === undefined ? true : savedTypeVisibility.log));
		$('#graphTypeToggleOther').prop('checked', (savedTypeVisibility.other === undefined ? true : savedTypeVisibility.other));

		$('#fruchterman-controlElement').on('click', function() {
			graphBrowser.doLayout('fruchtermanReingold');
		});

		$('#dagre-controlElement').on('click', function() {
			graphBrowser.doLayout('dagre');
		});

		$('#start-forceAtlas-controlElement').on('click', function() {
			graphBrowser.startForceAtlas2();
		});

		$('#stop-forceAtlas-controlElement').on('click', function() {
			graphBrowser.stopForceAtlas2();
		});

		$('#toggleNodeLabels').on('click', function() {
			nodeLabelsHidden = !nodeLabelsHidden;
			$(this).text( (nodeLabelsHidden ? 'Show' : 'Hide') + ' node labels');
			graphBrowser.changeSigmaSetting('drawLabels', !nodeLabelsHidden);
		});

		$('#toggleEdgeLabels').on('click', function() {
			edgeLabelsHidden = !edgeLabelsHidden;
			$(this).text((edgeLabelsHidden ? 'Show' : 'Hide') + ' edge labels');
			graphBrowser.changeSigmaSetting('drawEdgeLabels', !edgeLabelsHidden);
		});

		$('#selectionLasso').on('click', function() {
			if (!graphBrowser.selectionToolsActive) {
				graphBrowser.activateSelectionLasso(true);
			}
		});

		$('#newSelectionGroup').on('click', async function() {

			let newId = graphBrowser.createSelectionGroup();

			let html = await Structr.fetchHtmlTemplate('graph/new_selection_group', {newId: newId});

			$('#selectiontools-selectionTable-groupSelectionItems').append(html);
			$("input[name='selectedGroup[]']").trigger('click');
			$("input[value='selected." + newId + "']").prop('checked', true);
		});

		$(document).on('click', ".selectionTableRemoveBtn", function() {
			var val = $(this).val();
			graphBrowser.dropSelection(val);
		});

		$(document).on('click', "input[name='selectedGroup[]']",  function() {
			var self = $(this);

			if (self.is(':checked')) {
				$("input[name='selectedGroup[]']").prop("checked", false);
				self.prop("checked", true);
				var val = self.val().split('.');
				graphBrowser.activateSelectionTools();
				graphBrowser.activateSelectionGroup(val[1]);
			} else {
				graphBrowser.deactivateSelectionTools();
				self.prop("checked", false);
			}
		});

		$(document).on('click', "input[name='Hidden[]']",  function() {
			var self = $(this);
			var val = self.val().split('.');

			graphBrowser.hideSelectionGroup(val[1], self.is(':checked'));
		});

		$(document).on('click', "input[name='Fixed[]']",  function() {
			var self = $(this);
			var val = self.val().split('.');

			graphBrowser.fixateSelectionGroup(val[1], self.is(':checked'));
		});

		$(document).on('click', '.closeTooltipBtn', function(){
			graphBrowser.closeTooltip();
		});

		$(document).on('click', '#tooltipBtnProps', function(){
			var id = $(this).attr("value");
			_Entities.showProperties({id: id});
			graphBrowser.closeTooltip();
		});

		$(document).on('click', '#tooltipBtnHide', function() {
			var id = $(this).attr("value");
			graphBrowser.closeTooltip();
			graphBrowser.hideNode(id, true);
		});

		$(document).on('click', '#tooltipBtnDrop', function() {
			var id = $(this).attr("value");
			graphBrowser.closeTooltip();
			graphBrowser.dropNode(id);
			graphBrowser.dataChanged();
			_Graph.updateRelationshipTypes();
		});

		$(document).on('click', '#tooltipBtnDel', function() {
			var self = $(this);
			var id = self.attr("value");
			Command.get(id, 'id,type,name,sourceId,targetId', function (entity) {
				if (graphBrowser.getNode(entity.id)) {
					_Entities.deleteNode(self, entity, false, function (entity) {
						graphBrowser.dropNode(entity);
						graphBrowser.dataChanged();
						_Graph.updateRelationshipTypes();
					});
				} else {
					_Entities.deleteEdge(self, entity, false, function (entity) {
						if(graphBrowser.getEdge(entity)) {
							graphBrowser.dropEdge(entity);
						}
						graphBrowser.dataChanged();
						_Graph.updateRelationshipTypes();
					});
				}
			});
			graphBrowser.closeTooltip();
		});

		graph = $('#graph-canvas');

		graph.droppable({
			accept: '.node-type',
			drop: function(e, ui) {
				var nodeType = ui.helper.attr('data-node-type');
				Command.create({
					type: nodeType
				}, function(obj) {
					if(obj != null) {
						Command.get(obj.id, 'id,type,name,color,tag', function(node) {
							_Graph.drawNode(node);
						});
					}
				});
			}
		});

		_Graph.init();

		$('.slideOut').on('mouseover', function() {
			running = false;
			return true;
		});

		$('.slideOut').on('mouseout', function() {
			running = true;
			return true;
		});

		$('#queriesTab').on('click', function() {
			_Pages.leftSlideoutTrigger(this, queriesSlideout, [displaySlideout, filtersSlideout], activeTabLeftGraphKey);
		});

		$('#displayTab').on('click', function() {
			_Pages.leftSlideoutTrigger(this, displaySlideout, [queriesSlideout, filtersSlideout], activeTabLeftGraphKey);
		});

		$('#filtersTab').on('click', function() {
			_Pages.leftSlideoutTrigger(this, filtersSlideout, [queriesSlideout, displaySlideout], activeTabLeftGraphKey);
		});

		if (activeTabLeftGraph) {
			$('#' + activeTabLeftGraph).addClass('active').click();
		}

		if (activeTabRightGraph) {
			$('#' + activeTabRightGraph).addClass('active').click();
		}

		_Graph.appendCypherParameter($('#cypher-params'));

		$('#clear-graph').on('click', function() {
			_Graph.clearGraph();
		});

		$('.clearSearchIcon').on('click', function() {
			var self = $(this);
			self.hide();
			$('.search[name=' + self.data('type') + ']').val('').focus();
		});

		$('#exec-rest').on('click', function() {
			var query = $('.search[name=rest]').val();
			if (query && query.length) {
				_Graph.execQuery(query, 'rest');
			}
		});

		$('#exec-cypher').on('click', function() {
			var query = $('.search[name=cypher]').val();
			var params = {};
			var names = $.map($('[name="cyphername[]"]'), function(n) {
				return $(n).val();
			});
			var values = $.map($('[name="cyphervalue[]"]'), function(v) {
				return $(v).val();
			});

			for (var i = 0; i < names.length; i++) {
				params[names[i]] = values[i];
			}

			if (query && query.length) {
				_Graph.execQuery(query, 'cypher', JSON.stringify(params));
			}
		});

		$('#add-cypher-parameter').on('click', function() {
			_Graph.appendCypherParameter($('#cypher-params'));
		});

		$(document).on('click', '.remove-cypher-parameter', function() {
			$(this).parent().remove();
		});

		_Graph.clearSearch('rest');
		_Graph.clearSearch('cypher');
		_Graph.listSavedQueries();

		_Graph.searchField = $('.search', queriesSlideout);
		_Graph.searchField.focus();
		_Graph.searchField.keyup(function(e) {
			var self = $(this);
			var searchString = self.val();
			var type = self.attr('name');

			if (searchString && searchString.length) {
				_Graph.showClearSearchIcon(type);
			} else {
				_Graph.clearSearch(type);
			}

			if (searchString && searchString.length && e.which === 13) {
				if (!shiftKey) {
					_Graph.execQuery(searchString, type);
					return false;
				}
			} else if (e.which === 27) {
				_Graph.clearSearch(type);
			}
		});

		$(window).off('resize').resize(function() {
			_Graph.resize();
		});

		$('#newSelectionGroup').trigger('click');
		Structr.unblockMenu(100);

	},
	execQuery: function(query, type, params) {
		if (query && query.length) {
			if (type === 'cypher') {
				Command.cypher(query.replace(/(\r\n|\n|\r)/gm, ''), params, _Graph.processQueryResults);
				_Graph.saveQuery(query, 'cypher', params);
			} else {
				Command.rest(query.replace(/(\r\n|\n|\r)/gm, ''), _Graph.processQueryResults);
				_Graph.saveQuery(query, 'rest');
			}

			_Graph.listSavedQueries();
		}
	},
	processQueryResults: function (results) {
		var nodes = [];
		var rels  = [];

		$(results).each(function (i, entity) {
			if (entity.hasOwnProperty('relType')) {
				rels.push(entity);
			} else {
				nodes.push(entity);
			}
		});

		nodes.forEach(function (entity) {
			StructrModel.createSearchResult(entity);
		});

		rels.forEach(function (entity) {
			StructrModel.createSearchResult(entity);
		});

		graphBrowser.dataChanged();
		_Graph.updateRelationshipTypes();

	},
	saveQuery: function(query, type, params) {
		var savedQueries = JSON.parse(LSWrapper.getItem(savedQueriesKey)) || [];
		var exists = false;
		$.each(savedQueries, function(i, q) {
			if (q.query === query && q.params === params) {
				exists = true;
			}
		});
		if (!exists) {
			savedQueries.unshift({type: type, query: query, params: params});
			LSWrapper.setItem(savedQueriesKey, JSON.stringify(savedQueries));
		}
	},
	removeSavedQuery: function(i) {
		var savedQueries = JSON.parse(LSWrapper.getItem(savedQueriesKey)) || [];
		savedQueries.splice(i, 1);
		LSWrapper.setItem(savedQueriesKey, JSON.stringify(savedQueries));
		_Graph.listSavedQueries();
	},
	restoreSavedQuery: function(i, exec) {
		var savedQueries = JSON.parse(LSWrapper.getItem(savedQueriesKey)) || [];
		var query = savedQueries[i];
		$('.search[name=' + query.type + ']').val(query.query);
		_Graph.showClearSearchIcon(query.type);
		$('#cypher-params div.cypher-param').remove();
		if (query.params && query.params.length) {
			var parObj = JSON.parse(query.params);
			$.each(Object.keys(parObj), function(i, key) {
				_Graph.appendCypherParameter($('#cypher-params'), key, parObj[key]);
			});
		} else {
			_Graph.appendCypherParameter($('#cypher-params'));
		}
		if (exec) {
			_Graph.execQuery(query.query, query.type, query.params);
		}
	},
	listSavedQueries: function() {
		$('#saved-queries').empty();

		var savedQueries = JSON.parse(LSWrapper.getItem(savedQueriesKey)) || [];
		$.each(savedQueries, function(q, query) {
			if (query.type === 'cypher') {
				$('#saved-queries').append('<div class="saved-query cypher-query"><i class="replay ' + _Icons.getFullSpriteClass(_Icons.exec_icon) + '" />' + query.query + '<i class="remove-query ' + _Icons.getFullSpriteClass(_Icons.grey_cross_icon) + '" /></div>');
			} else {
				$('#saved-queries').append('<div class="saved-query rest-query"><i class="replay ' + _Icons.getFullSpriteClass(_Icons.exec_blue_icon) + '" />' + query.query + '<i class="remove-query ' + _Icons.getFullSpriteClass(_Icons.grey_cross_icon) + '" /></div>');
			}
		});

		$('.saved-query').on('click', function() {
			_Graph.restoreSavedQuery($(this).index());
		});

		$('.replay').on('click', function() {
			_Graph.restoreSavedQuery($(this).parent().index(), true);
		});

		$('.remove-query').on('click', function() {
			_Graph.removeSavedQuery($(this).parent().index());
		});
	},

	showClearSearchIcon: function(type) {
		$('#clear-' + type).show();
	},

	clearSearch: function(type) {
		$('#clear-' + type).hide();
		$('.search[name=' + type + ']').val('').focus();
	},

	clearGraph: function() {
		colors = [];
		relTypes = {};
		nodeTypes = {};
		nodeIds = [];
		relIds = [];
		hiddenNodeTypes = [];
		hiddenRelTypes = [];
		graphBrowser.hideExpandButtons();
		graphBrowser.reset();
		_Graph.updateRelationshipTypes();
	},

	unload: function() {
		colors = [];
		relTypes = {};
		nodeTypes = {};
		nodeIds = [];
		relIds = [];
		hiddenNodeTypes = [];
		hiddenRelTypes = [];
		if (graphBrowser) {
			graphBrowser.kill();
			graphBrowser = undefined;
		}
	},

	drawNode: function(node, x, y) {
		if (isIn(node.id, nodeIds) || isIn(node.type, filteredNodeTypes)) {
			return;
		}
		nodeIds.push(node.id);
		_Graph.setNodeColor(node);

		try {
			var ratio = graphBrowser.getCameraRatio();
			var newX = 0;
			var newY = 0;

			if (ratio > 1) {
				newX = (Math.random(100) / (2*ratio));
				newY = (Math.random(100) / (2*ratio));
			} else {
				newX = (Math.random(100) / (1/ratio));
				newY = (Math.random(100) / (1/ratio));
			}

			graphBrowser.addNode({
				id: node.id || node.name,
				label: (node.name || node.tag || node.id.substring(0, 5) + '…') ,
				x: x || newX,
				y: y || newY,
				size: 20,
				color: color[node.type],
				nodeType: node.type,
				name: node.name,
				hidden: isIn(node.type, hiddenNodeTypes)
			});

			graphBrowser.dataChanged();

		} catch (error) {
//			console.log('Node: ' + node.id + 'already in the graph');
		}
	},

	drawRel: function(r) {

		var existingEdges = graphBrowser.findRelationships(r.sourceId, r.targetId);
		var c = existingEdges.length * 15;

		try{
			graphBrowser.addEdge({
				id: r.id,
				label: r.relType,
				source: r.sourceId,
				target: r.targetId,
				size: 40,
				color: defaultRelColor,
				type: edgeType,
				relType: r.type,
				relName: r.relType,
				hidden: isIn(r.relType, hiddenRelTypes),
				count: c
			});
			_Graph.updateRelationshipTypes();
		}
		catch(error){
//			console.log('Edge: ' + r.id + 'already in the graph');
		}

	},

	resize: function() {
		Structr.resize();

		var windowHeight = $(window).height();
		var windowWidth = $(window).width();

		var ch = windowHeight - graph.offset().top;

		graph.css({
			height: ch,
			width: windowWidth
		});

		$('canvas', graph).css({
			height: ch,
			width: windowWidth
		});

		nodeTypes = $('#node-types');
		var distance = nodeTypes.position().top - 60;
		var boxHeight = (ch - (3 * distance)) / 2;

		nodeTypes.css({
			height: boxHeight
		});

		$('#relationship-types').css({
			top: nodeTypes.position().top + boxHeight + distance,
			height: boxHeight
		});
	},

	loadTypeDefinition: function(type, callback) {
		var url = rootUrl + '_schema/' + type;
		$.ajax({
			url: url,
			dataType: 'json',
			contentType: 'application/json; charset=utf-8',
			statusCode: {
				200: function(data) {
					if (callback) {
						callback(data.result[0]);
					}
				},
				401: function(data) {
					console.log(data);
				},
				404: function(data) {
					console.log(data);
				},
				422: function(data) {
					console.log(data);
				}
			}
		}).always(function(data) {
			if (callback) {
				callback(data.result[0]);
			}
		});
	},
	getTypeVisibilityConfig: function () {

		return {
			rels:   $('#graphTypeToggleRels').prop('checked'),
			custom: $('#graphTypeToggleCustom').prop('checked'),
			core:   $('#graphTypeToggleCore').prop('checked'),
			html:   $('#graphTypeToggleHtml').prop('checked'),
			ui:     $('#graphTypeToggleUi').prop('checked'),
			log:    $('#graphTypeToggleLog').prop('checked'),
			other:  $('#graphTypeToggleOther').prop('checked')
		};

	},
	updateNodeTypes: function() {

		var nodeTypesBox = $('#node-types');
		fastRemoveAllChildren(nodeTypesBox[0]);

		Command.getSchemaInfo(null, function(nodes) {

			var typeVisibility = _Graph.getTypeVisibilityConfig();

			filteredNodeTypes = [];

			nodes.sort(function(a, b) {
				var aName = a.name.toLowerCase();
				var bName = b.name.toLowerCase();
				return aName < bName ? -1 : aName > bName ? 1 : 0;
			});

			nodes.forEach(function(node) {

				if (node.isRel === true) {
					return;
				}

				var isRelType     = node.isRel;
				var isDynamicType = node.className.startsWith('org.structr.dynamic');
				var isCoreType    = node.className.startsWith('org.structr.core.entity');
				var isHtmlType    = node.className.startsWith('org.structr.web.entity.html');
				var isUiType      = node.className.startsWith('org.structr.web.entity') && !node.className.startsWith('org.structr.web.entity.html');
				var isLogType     = node.className.startsWith('org.structr.rest.logging.entity');
				var isOtherType   = !(isRelType || isDynamicType || isCoreType || isHtmlType || isUiType || isLogType);

				var hide =	(!typeVisibility.rels && isRelType) || (!typeVisibility.custom && isDynamicType) || (!typeVisibility.core && isCoreType) || (!typeVisibility.html && isHtmlType) ||
							(!typeVisibility.ui && isUiType) || (!typeVisibility.log && isLogType) || (!typeVisibility.other && isOtherType);

				if (hide) {
					filteredNodeTypes.push(node.type);
					return;
				}

				schemaNodes[node.type] = node;
				schemaNodesById[node.id] = node;

				// expand comma-separated list into real collection
				if (schemaNodes[node.type].possibleSourceTypes) {
					schemaNodes[node.type].possibleSourceTypes = schemaNodes[node.type].possibleSourceTypes.split(",");
				}

				// expand comma-separated list into real collection
				if (schemaNodes[node.type].possibleTargetTypes) {
					schemaNodes[node.type].possibleTargetTypes = schemaNodes[node.type].possibleTargetTypes.split(",");
				}

				var nodeType = node.name;

				if (!isIn(nodeType, Object.keys(color))) {
					color[nodeType] = colors[c++];
				}

				nodeTypesBox.append('<div id="node-type-' + nodeType + '" class="node-type" data-node-type="' + nodeType + '"><input type="checkbox" class="toggle-type" checked="checked"> <div class="circle" style="background-color: ' + color[nodeType] + '"></div>' + nodeType + '</div>');
				var nt = $('#node-type-' + nodeType, nodeTypesBox);

				if (isIn(nodeType, hiddenNodeTypes)) {
					nt.attr('data-hidden', 1);
					nt.addClass('hidden-node-type');
				}
				nt.on('mousedown', function() {
					var nodeTypeEl = $(this);
					nodeTypeEl.css({pointer: 'move'});
				}).on('click', function() {
					// TODO: Query
				}).on('mouseover', function() {
					graphBrowser.highlightNodeType(nodeType);
				}).on('mouseout', function() {
					graphBrowser.unhighlightNodeType(nodeType);
				}).draggable({
					helper: 'clone'
				});

				$('.toggle-type', nt).on('click', function() {
					var n = $(this);
					if (n.attr('data-hidden')) {
						graphBrowser.hideNodeType(nodeType, false);
						n.removeAttr('data-hidden', 1);
					} else {
						graphBrowser.hideNodeType(nodeType, true);
						n.attr('data-hidden', 1);
					}
				});
			});
			_Graph.filterNodeTypes(filteredNodeTypes);
			_Graph.resize();
		});
	},

	filterNodeTypes: function(types) {
			graphBrowser.clearFilterNodeTypes();
		types.forEach(function(type) {
			graphBrowser.addNodeTypeToFilter(type);
		});
			graphBrowser.filterGraph();
	},

	setNodeColor: function(node) {
		if (!isIn(node.type, Object.keys(color))) {
			node.color = colors[color++];
			color[node.type] = node.color;
		} else {
			node.color = color[node.type];
		}
	},

	setRelationshipColor: function(rel) {
		if (!isIn(rel.relType, Object.keys(relColors))) {
			rel.color = colors[color++];
			relColors[rel.relType] = rel.color;
		} else {
			rel.color = relColors[rel.relType];
		}
	},

	updateRelationshipTypes: function() {
		var relTypesBox = $('#relationship-types');
		relTypesBox.empty();
		var relTypes = graphBrowser.getCurrentRelTypes();
		$.each(relTypes, function(i, relType){
			relTypesBox.append('<div id="rel-type-' + relType + '">' + relType + '</div>');
			var rt = $('#rel-type-' + relType, relTypesBox);
			if (isIn(relType, hiddenRelTypes)) {
				rt.attr('data-hidden', 1);
				rt.addClass('hidden-node-type');
			}
			rt.on('mousedown', function() {
				var relTypeEl = $(this);
				relTypeEl.css({pointer: 'move'});
			}).on('click', function() {
				var n = $(this);
				if (n.attr('data-hidden')) {
					graphBrowser.hideRelType(relType, false);
					n.removeAttr('data-hidden', 1);
					n.removeClass('hidden-node-type');
				} else {
					graphBrowser.hideRelType(relType, true);
					n.attr('data-hidden', 1);
					n.addClass('hidden-node-type');
				}
			}).on('mouseover', function() {
					graphBrowser.highlightRelType(relType);
			}).on('mouseout', function() {
					graphBrowser.unhighlightRelType(relType);
			});
		});
	},

	appendCypherParameter: function(el, key, value) {
		el.append('<div class="cypher-param"><i class="remove-cypher-parameter ' + _Icons.getFullSpriteClass(_Icons.delete_icon) + '" /> <input name="cyphername[]" type="text" placeholder="name" size="10" value="' + (key || '') + '"> <input name="cyphervalue[]" type="text" placeholder="value" size="10" value="' + (value || '') + '"></div>');
	},

	onNodesAdded: function(){
		_Graph.updateRelationshipTypes();
	},

	handleClickStageEvent: function(){
		graphBrowser.hideExpandButtons();
	},

	handleDragNodeEvent: function(){
		hasDragged = true;
	},

	handleStartDragNodeEvent: function(){
		hasDragged = true;
		graphBrowser.hideExpandButtons();
	},

	handleDoubleClickNodeEvent: function(clickedNode){
		window.clearTimeout(clickTimeout);
		hasDoubleClicked = true;
		return false;
	},

	handleClickNodeEvent: function(clickedNode){
		var node = clickedNode.data.node;

		if (hasDoubleClicked) {
			return false;
		}

		if (hasDragged) {
			hasDragged = false;
			return false;
		}

		clickTimeout = window.setTimeout(function() {
			_Entities.showProperties(node);
			window.clearTimeout(clickTimeout);
		}, doubleClickTime);

		window.setTimeout(function() {
			hasDoubleClicked = false;
		}, doubleClickTime + 10);
	},

	handleClickEdgeEvent: function(clickedEdge){
		var edge = clickedEdge.data.edge;

		if (hasDragged) {
			hasDragged = false;
			return false;
		}

		hasDoubleClicked = false;
		_Entities.showProperties(edge);
	},

	renderNodeExpanderInfoButton: function(colorKey, label){
		var button =
			'<div class="nodeExpander-infobutton">' +
				'<div class="circle" style="background-color: ' + (color[colorKey] || '#e5e5e5')+ '"></div>' + label +
			'</div>';
		return button;
	},

	renderNodeTooltip: function(node){
		var tooltip =
			"<div class='tooltipArrowUp'></div>" +
			"<div class='graphTooltip'>" +
				"<div class='tooltipHeader'>" +
					"<i class='closeTooltipBtn " + _Icons.getFullSpriteClass(_Icons.grey_cross_icon) + "'></i>" +
					"<p class='tooltipTitle'>" + node.label + "</p>" +
				"</div>" +
				"<div class='tooltipContent' style='border-top: solid " + color[node.nodeType] + " 4px;'>" +
					"<div class='tooltipBody'>" +
						 "<table class='tooltipTable'>" +
							"<tbody>" +
								"<tr>" +
									"<td class='tooltipTableLabel'>Id: </td><td class='graphTooltipSelectable'>" + node.id + "</td>" +
								"</tr><tr>" +
									"<td class='tooltipTableLabel'>Type: </td><td class='graphTooltipSelectable'>" + node.nodeType + "</td>" +
								"</tr>" +
							"</tbody>" +
						"</table>" +
					"</div>" +
					"<div id='tooltipFooter" + node.id + "' class='tooltipFooter'>" +
						"<div><button id='tooltipBtnProps' value='" + node.id + "'>Properties</button>" +
						"<button id='tooltipBtnDrop' value='" + node.id + "'>Remove</button>" +
						"<button id='tooltipBtnDel' value='" + node.id + "'>Delete</button></div>" +
					"</div>" +
				"</div>" +
			"</div>";
		graphBrowser.hideExpandButtons();
		return tooltip;
	},
	renderEdgeTooltip: function(edge){
		var tooltip =
			"<div class='tooltipArrowUp'></div>" +
			"<div class='graphTooltip'>" +
				"<div class='tooltipHeader'>" +
					"<p class='tooltipTitle'>" + edge.label + "</p>" +
					"<i class='closeTooltipBtn " + _Icons.getFullSpriteClass(_Icons.grey_cross_icon) + "'></i>" +
				"</div>" +
				"<div class='tooltipBody'>" +
					"<table class='tooltipTable'>" +
						"<tbody>" +
							"<tr>" +
								"<td class='tooltipTableLabel'>Id: </td><td class='graphTooltipSelectable'>" + edge.id + "</td>" +
							"</tr><tr>" +
								"<td class='tooltipTableLabel'>relType: </td><td class='graphTooltipSelectable'>" + edge.relType + "</td>" +
							"</tr><tr>" +
								"<td class='tooltipTableLabel'>Source: </td><td class='graphTooltipSelectable'>" + edge.source + "</td>" +
							"</tr><tr>" +
								"<td class='tooltipTableLabel'>Target: </td><td class='graphTooltipSelectable edgeTableEl'>" + edge.target + "</td>" +
							"</tr>" +
						"</tbody>" +
					"</table>" +
				"</div>" +
				"<div id='edgeTooltipFooter' class='tooltipFooter'>" +
					"<div><button id='tooltipBtnProps' value='" + edge.id + "'>Properties</button>" +
					"<button id='tooltipBtnDel' value='" + edge.id + "'>Delete</button></div>" +
				"</div>" +
			"</div>";
		return tooltip;
	}
};