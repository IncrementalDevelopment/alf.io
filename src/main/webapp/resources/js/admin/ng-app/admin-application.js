(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angular-templates/admin/partials";
    var PAYMENT_PROXY_DESCRIPTIONS = {
        'STRIPE': 'Credit card payments',
        'ON_SITE': 'On site (cash) payment',
        'OFFLINE': 'Offline payment (bank transfer, invoice, etc.)'
    };
    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'utilFilters', 'ngMessages', 'ngFileUpload', 'chart.js', 'nzToggle', 'alfio-plugins', 'alfio-email', 'alfio-util', 'alfio-configuration', 'alfio-users']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController',
                data: {
                    eventType: 'INTERNAL'
                }
            })
            .state('events.newLink', {
                url: '/new-external',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController',
                data: {
                    eventType: 'EXTERNAL'
                }
            })
            .state('events.detail', {
                url: '/:eventName',
                templateUrl: BASE_STATIC_URL + '/event/detail.html',
                controller: 'EventDetailController'
            })
            .state('events.checkIn', {
                url: '/:eventName/check-in',
                templateUrl: BASE_STATIC_URL + '/event/check-in.html',
                controller: 'EventCheckInController'
            })
            .state('events.checkInScan', {
                url: '/:eventName/check-in/scan',
                templateUrl: BASE_STATIC_URL + '/event/check-in-scan.html',
                controller: 'EventCheckInScanController'
            })
            .state('events.sendInvitations', {
                url: '/:eventName/c/:categoryId/send-invitation',
                templateUrl: BASE_STATIC_URL + '/event/fragment/send-reserved-codes.html',
                controller: 'SendInvitationsController'
            })
            .state('pending-reservations', {
                url: '/pending-reservations/:eventName/',
                templateUrl: BASE_STATIC_URL + '/pending-reservations/index.html',
                controller: 'PendingReservationsController'
            })
            .state('compose-custom-message', {
                url: '/compose-custom-message/:eventName',
                templateUrl: BASE_STATIC_URL + '/custom-message/index.html',
                controller: 'ComposeCustomMessage'
            })
            .state('events.show-waiting-queue', {
                url: '/:eventName/waiting-queue',
                templateUrl: BASE_STATIC_URL + '/waiting-queue/index.html',
                controller: 'ShowWaitingQueue as ctrl'
            });

        var printLabel = function(val) {
            return val.label + ' ('+ val.value +')';
        };

        Chart.defaults.global.multiTooltipTemplate = function(val) {
            return printLabel(val);
        };
        Chart.defaults.global.tooltipTemplate = function(val) {
            return printLabel(val);
        };
        Chart.defaults.global.colours = [
            { // yellow
                fillColor: "rgba(253,180,92,0.2)",
                strokeColor: "rgba(253,180,92,1)",
                pointColor: "rgba(253,180,92,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(253,180,92,0.8)"
            },
            { // green
                fillColor: "rgba(70,191,189,0.2)",
                strokeColor: "rgba(70,191,189,1)",
                pointColor: "rgba(70,191,189,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(70,191,189,0.8)"
            },
            { // blue
                fillColor: "rgba(151,187,205,0.2)",
                strokeColor: "rgba(151,187,205,1)",
                pointColor: "rgba(151,187,205,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(151,187,205,0.8)"
            },
            { // light grey
                fillColor: "rgba(220,220,220,0.2)",
                strokeColor: "rgba(220,220,220,1)",
                pointColor: "rgba(220,220,220,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(220,220,220,0.8)"
            },
            { // purple
                fillColor: "rgba(158,31,255,0.2)",
                strokeColor: "rgba(158,31,255,1)",
                pointColor: "rgba(158,31,255,0.2)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(158,31,255,0.8)"
            }

        ];
    });

    navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

    admin.run(function($rootScope, $modal, $window, $state) {
        $rootScope.$on('ErrorNotAuthorized', function() {
            $modal.open({
                size:'sm',
                templateUrl:'/resources/angular-templates/admin/partials/error/not-authorized.html'
            }).result.then(angular.noop, function() {
                $state.go('index');
            });
        });
        $rootScope.$on('ErrorNotLoggedIn', function() {
            $modal.open({
                size:'sm',
                templateUrl:'/resources/angular-templates/admin/partials/error/not-logged-in.html'
            }).result.then(angular.noop, function() {
                $window.location.reload();
            });
        });
    });

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
                deferred.reject('invalid form');
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    admin.controller('MenuController', function($scope) {
        $scope.menuCollapsed = true;
        $scope.toggleCollapse = function(currentStatus) {
            $scope.menuCollapsed = !currentStatus;
        };
    });

    var createCategory = function(sticky, $scope, expirationExtractor) {
        var lastCategory = _.last($scope.event.ticketCategories);
        var inceptionDate, notBefore;
        if(angular.isDefined(lastCategory)) {
            var lastExpiration = angular.isFunction(expirationExtractor) ? expirationExtractor(lastCategory) : lastCategory.expiration.date;
            inceptionDate = moment(lastExpiration).format('YYYY-MM-DD');
            notBefore = inceptionDate;
        } else {
            inceptionDate = moment().format('YYYY-MM-DD');
            notBefore = undefined;
        }

        return {
            inception: {
                date: inceptionDate
            },
            tokenGenerationRequested: false,
            expiration: {},
            sticky: sticky,
            notBefore: notBefore,
            bounded: true
        };

    };

    var createAndPushCategory = function(sticky, $scope, expirationExtractor) {
        $scope.event.ticketCategories.push(createCategory(sticky, $scope, expirationExtractor));
    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state) {
        $scope.organizations = {};

        $scope.isInternal = function(event) {
            return event.type === 'INTERNAL';
        };

        EventService.getSupportedLanguages().success(function(result) {
            $scope.allLanguages = result;
            $scope.allLanguagesMapping = {};
            angular.forEach(result, function(r) {
                $scope.allLanguagesMapping[r.value] = r;
            });
        });

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        PaymentProxyService.getAllProxies().success(function(result) {
            $scope.allowedPaymentProxies = _.map(result, function(p) {
                return {
                    id: p,
                    description: PAYMENT_PROXY_DESCRIPTIONS[p] || 'Unknown provider ('+p+')  Please check configuration'
                };
            });
        });

        $scope.addCategory = function() {
            createAndPushCategory(false, $scope);
        };

        $scope.canAddCategory = function(categories) {
            var remaining = _.foldl(categories, function(difference, category) {
                var categoryTickets = category.bounded ? category.maxTickets : 0;
                return difference - categoryTickets;
            }, $scope.event.availableSeats);

            var isDefinedMaxTickets = function(category) {
                return !category.bounded || (angular.isDefined(category.maxTickets) && category.maxTickets > 0);
            };

            return remaining > 0 && _.every(categories, function(category) {
                return angular.isDefined(category.name) &&
                    isDefinedMaxTickets(category)  &&
                    angular.isDefined(category.expiration.date);
            });
        };

        $scope.cancel = function() {
            $state.go('index');
        };

        //----------

        // TODO, change, HARDCODED
        $scope.fieldTypes = ['input:text', 'input:tel', 'textarea', 'select', 'country'];

        $scope.addNewTicketField = function(event) {
            if(!event.ticketFields) {
                event.ticketFields = [];
            }

            event.ticketFields.push({required:false, order: event.ticketFields.length+1, type:'input:text', maxLength:255});
        };

        $scope.removeTicketField = function(fields, field) {
            var index = fields.indexOf(field);
            fields.splice( index, 1 )
        };

        $scope.isLanguageSelected = function(lang, selectedLanguages) {
            return (selectedLanguages & lang) > 0;
        };

        $scope.addRestrictedValue = function(field) {
            var arr = field.restrictedValues || [];
            arr.push({});
            field.restrictedValues = arr;
        }

    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService) {

        var eventType = $state.$current.data.eventType;
        $scope.event = {
            type: eventType,
            freeOfCharge: false,
            begin: {},
            end: {},
            locales: 7
        };
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state);
        $scope.event.ticketCategories = [];
        if(eventType === 'INTERNAL') {
            createAndPushCategory(true, $scope);
        }

        $scope.save = function(form, event) {
            /*if(!form.$valid) {
                return;
            }*/
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    $state.go('index');
                });
            }, angular.noop);
        };

    });

    admin.controller('EventDetailController', function ($scope,
                                                        $stateParams,
                                                        OrganizationService,
                                                        PromoCodeService,
                                                        EventService,
                                                        LocationService,
                                                        $rootScope,
                                                        PaymentProxyService,
                                                        $state,
                                                        $log,
                                                        $q,
                                                        $window,
                                                        $modal) {
        var loadData = function() {
            $scope.loading = true;
            EventService.getEvent($stateParams.eventName).success(function(result) {
                $scope.event = result.event;
                $scope.organization = result.organization;
                $scope.validCategories = _.filter(result.event.ticketCategories, function(tc) {
                    return !tc.expired && tc.bounded;
                });
                $scope.loading = false;
                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).success(function(mapUrl) {
                    $scope.event.geolocation = {
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });
                
                
                PromoCodeService.list(result.event.id).success(function(list) {
                    $scope.promocodes = list;
                    angular.forEach($scope.promocodes, function(v) {
                        (function(v) {
                            PromoCodeService.countUse(result.event.id, v.promoCode).then(function(val) {
                                v.useCount = parseInt(val.data, 10);
                            });
                        })(v);
                    });
                });

                $scope.unbindTickets = function(event , category) {
                    EventService.unbindTickets(event, category).success(function() {
                        loadData();
                    });
                };
            });
        };
        loadData();
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, EventService, $state);
        $scope.evaluateCategoryStatusClass = function(index, category) {
            if(category.expired) {
                return 'category-expired';
            }
            return 'category-' + $rootScope.evaluateBarType(index);
        };

        $scope.evaluateClass = function(token) {
            switch(token.status) {
                case 'WAITING':
                    return 'bg-warning fa fa-cog fa-spin';
                case 'FREE':
                    return 'fa fa-qrcode';
                case 'TAKEN':
                    return 'bg-success fa fa-check';
                case 'CANCELLED':
                    return 'bg-default fa fa-eraser';
            }
        };

        $scope.selection = {
            active: true,
            expired: false,
            freeText: ''
        };

        $scope.isTokenViewCollapsed = function(category) {
            return !category.isTokenViewExpanded;
        };

        $scope.isTicketViewCollapsed = function(category) {
            return !category.isTicketViewExpanded;
        };

        $scope.toggleTokenViewCollapse = function(category) {
            category.isTokenViewExpanded = !category.isTokenViewExpanded;
        };

        $scope.toggleTicketViewCollapse = function(category) {
            category.isTicketViewExpanded = !category.isTicketViewExpanded;
        };

        $scope.evaluateTicketStatus = function(status) {
            var cls = 'fa ';

            switch(status) {
                case 'PENDING':
                    return cls + 'fa-warning text-warning';
                case 'ACQUIRED':
                    return cls + 'fa-bookmark text-success';
                case 'TO_BE_PAID':
                    return cls + 'fa-bookmark-o text-success';
                case 'CHECKED_IN':
                    return cls + 'fa-check-circle text-success';
                case 'CANCELLED':
                    return cls + 'fa-close text-danger';
            }

            return cls + 'fa-cog';
        };

        $scope.isPending = function(token) {
            return token.status === 'WAITING';
        };

        $scope.isReady = function(token) {
            return token.status === 'WAITING';
        };

        $scope.moveOrphans = function(srcCategory, targetCategoryId, eventId) {
            EventService.reallocateOrphans(srcCategory, targetCategoryId, eventId).success(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.eventHeader = {};
        $scope.eventPrices = {};

        $scope.toggleEditHeader = function(editEventHeader) {
            $scope.editEventHeader = !editEventHeader;
        };

        $scope.toggleEditPrices = function(editPrices) {
            $scope.editPrices = !editPrices;
        };

        var validationErrorHandler = function(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] == 0) {
                    resolve(result);
                } else {
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            field.$setValidity('required', false);
                            field.$setTouched();
                        }
                    });
                    reject('validation error');
                }
            });
        };

        var errorHandler = function(error) {
            $log.error(error.data);
            alert(error.data);
        };

        $scope.saveEventHeader = function(form, header) {
            /*if(!form.$valid) {
                return;
            }*/
            EventService.updateEventHeader(header).then(function(result) {
                validationErrorHandler(result, form, form.editEventHeader).then(function(result) {
                    $scope.editEventHeader = false;
                    loadData();
                });
            }, errorHandler);
        };

        $scope.saveEventPrices = function(form, eventPrices, organizationId) {
            if(!form.$valid) {
                return;
            }
            var obj = {'organizationId':organizationId};
            angular.extend(obj, eventPrices);
            EventService.updateEventPrices(obj).then(function(result) {
                validationErrorHandler(result, form, form.editPrices).then(function(result) {
                    $scope.editPrices = false;
                    loadData();
                });
            }, errorHandler);
        };

        var parentScope = $scope;

        var openCategoryDialog = function(category, event) {
            var editCategory = $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
            return editCategory.result;
        };

        $scope.addCategory = function(event) {
            openCategoryDialog(createCategory(true, $scope, function(obj) {return obj.formattedExpiration}), event).then(function() {
                loadData();
            });
        };

        $scope.openConfiguration = function(event, category) {
            $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/category-configuration-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.allLanguagesMapping = parentScope.allLanguagesMapping;
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.modal = $modal;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
        };

        $scope.toggleLocking = function(event, ticket, category) {
            EventService.toggleTicketLocking(event, ticket, category).then(function() {
                loadData();
            });
        };

        $scope.editCategory = function(category, event) {
            var inception = moment(category.formattedInception);
            var expiration = moment(category.formattedExpiration);
            var categoryObj = {
                id: category.id,
                name: category.name,
                price: category.actualPrice,
                description: category.description,
                maxTickets: category.maxTickets,
                bounded: category.bounded,
                inception: {
                    date: inception.format('YYYY-MM-DD'),
                    time: inception.format('HH:mm')
                },
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                tokenGenerationRequested: category.accessRestricted,
                sticky: false
            };

            openCategoryDialog(categoryObj, event).then(function() {
                loadData();
            });
        };

        var getPendingPayments = function() {
            EventService.getPendingPayments($stateParams.eventName).success(function(data) {
                $scope.pendingReservations = data;
            });
        };

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        
        //
        
        $scope.deletePromocode = function(promocode) {
            if($window.confirm('Delete promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.remove($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
            }
        };
        
        $scope.disablePromocode = function(promocode) {
            if($window.confirm('Disable promo code ' + promocode.promoCode + '?')) {
                PromoCodeService.disable($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
            }
        };
        
        $scope.addPromoCode = function(event) {
            $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    
                    $scope.event = event;
                    
                    var now = moment();
                    var eventBegin = moment(event.formattedBegin);
                    
                    $scope.promocode = {discountType :'PERCENTAGE', start : {date: now.format('YYYY-MM-DD'), time: now.format('HH:mm')}, end: {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')}};
                    
                    $scope.$watch('promocode.promoCode', function(newVal) {
                        if(newVal) {
                            $scope.promocode.promoCode = newVal.toUpperCase();
                        }
                    });
                    
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, promocode, event) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);
                        
                        PromoCodeService.add(event.id, promocode).then(function(result) {
                            validationErrorHandler(result, form, form.promocode).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler).then(loadData);
                    };
                }
            });
        };

        $scope.countActive = function(categories) {
            return _.countBy(categories, 'expired')['false'] || '0';
        };

        $scope.countExpired = function(categories) {
            return _.countBy(categories, 'expired')['true'] || '0';
        };


        $scope.openFieldSelectionModal = function() {
            $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/select-field-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.selected = {};
                    EventService.getFields(parentScope.event.shortName).then(function(fields) {
                        $scope.fields = fields.data;
                        angular.forEach(fields.data, function(v) {
                            $scope.selected[v] = false;
                        })
                    });

                    $scope.selectAll = function() {
                        angular.forEach($scope.selected, function(v,k) {
                            $scope.selected[k] = true;
                        });
                    };

                    $scope.deselectAll = function() {
                        angular.forEach($scope.selected, function(v,k) {
                            $scope.selected[k] = false;
                        });
                    };

                    $scope.download = function() {

                        var queryString = "";

                        angular.forEach($scope.selected, function(v,k) {
                            if(v) {
                                queryString+="fields="+k+"&";
                            }
                        });

                        $window.open($window.location.pathname+"/api/events/"+parentScope.event.shortName+"/export.csv?"+queryString);
                    };
                }
            });
        };

    });

    admin.controller('SendInvitationsController', function($scope, $stateParams, $state, EventService, $log) {
        $scope.eventName = $stateParams.eventName;
        $scope.categoryId = $stateParams.categoryId;

        $scope.sendCodes = function(data) {
            EventService.sendCodesByEmail($stateParams.eventName, $stateParams.categoryId, data).success(function() {
                alert('Codes have been successfully sent');
                $state.go('events.detail', {eventName: $stateParams.eventName});
            }).error(function(e) {
                alert(e.data);
            });
        };

        $scope.uploadSuccess = function(data) {
            $scope.results = data;
        };
        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/categories/'+$stateParams.categoryId+'/link-codes';
    });
    
    admin.controller('EventCheckInController', function($scope, $stateParams, $timeout, $log, $state, EventService, CheckInService) {

        $scope.selection = {};

        $scope.advancedSearch = {};

        $scope.resetAdvancedSearch = function() {
            $scope.advancedSearch = {};
        }

        $scope.toggledAdvancedSearch = function(toggled) {
            if(toggled) {
                $scope.selection.freeText = undefined;
            } else {
                $scope.resetAdvancedSearch();
            }
        }

        $scope.goToScanPage = function() {
            $state.go('events.checkInScan', $stateParams);
        };

        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
            CheckInService.findAllTickets(result.event.id).success(function(tickets) {
                $scope.tickets = tickets;
            });
        });

        $scope.toBeCheckedIn = function(ticket, idx) {
            return  ['TO_BE_PAID', 'ACQUIRED'].indexOf(ticket.status) >= 0;
        }

        
        $scope.reloadTickets = function() {
            CheckInService.findAllTickets($scope.event.id).success(function(tickets) {
                $scope.tickets = tickets;
            });
        };

        $scope.manualCheckIn = function(ticket) {
            CheckInService.manualCheckIn(ticket).then($scope.reloadTickets).then(function() {
                $scope.selection = {};
            });
        }
    });

    admin.controller('EventCheckInScanController', function($scope, $stateParams, $timeout, $log, $state, EventService, CheckInService) {

        $scope.scanning = {visible : false, ticket : {}};


        var canReadCamera = MediaStreamTrack.getSources !== undefined;

        $scope.goToScanPage = function() {
            $state.go('events.checkInScan', $stateParams);
        };

        $scope.canReadCamera = canReadCamera;
        if(canReadCamera) {

            var processingScannedImage = false;

            navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
            $scope.videos = [];
            $scope.stream = null;

            var timeoutPromise = null;
            var worker = new Worker("../resources/js/jsqrcode/decode-worker.js");

            worker.addEventListener('message', function(message) {
                processingScannedImage = false;
                var result = message.data;
                $scope.scanning.loadingTicket = false;
                if(result === 'error decoding QR Code') {
                    $log.debug('error decoding qr code');
                } else if ($scope.scanning.scannedResult == null) {
                    $scope.$apply(function() {
                        $scope.scanning.visible = false;
                        $scope.scanning.ticket.code = result;
                        $scope.scanning.loadingTicket = true;

                        CheckInService.getTicket($scope.event.id, result).success(function(result) {
                            $scope.scanning.scannedTicketInfo = result.ticket;
                            $scope.scanning.scannedResult = result.result;
                            $scope.scanning.loadingTicket = false;
                        });
                    });
                } else {
                    $log.debug('scanned result already present, skipping');
                }
            }, false);

            var captureFrame = function() {
                if($scope.scanning.visible && $scope.scanning.scannedResult == null && !processingScannedImage) {
                    $log.debug('try to capture frame');
                    try {
                        var videoElement = document.getElementById('checkInVideoElement');
                        var canvas = document.getElementById("checkInImageCanvas");
                        canvas.height = videoElement.videoHeight;
                        canvas.width = videoElement.videoWidth;

                        canvas.getContext("2d").drawImage(videoElement, 0, 0);
                        var imageData = canvas.getContext("2d").getImageData(0,0,canvas.width, canvas.height);
                        worker.postMessage(imageData);
                        processingScannedImage = true;
                    } catch(e) {
                        processingScannedImage = false;
                        $log.debug('error', e)
                    }
                } else {
                    $log.debug('skipping');
                }

                timeoutPromise = $timeout(function() {
                    captureFrame();
                }, 250);
            }

            var endVideoStream = function () {
                processingScannedImage = false;
                if (!!$scope.stream) {
                    $scope.stream.stop();
                }
            }

            var stopScanning = function () {
                endVideoStream();
                $scope.resetScanning();
                $scope.scanning.visible = false;
                $timeout.cancel(timeoutPromise);
            }

            $scope.$on('$destroy', function() {
                worker.terminate();
                endVideoStream();
                stopScanning();
            });

            $scope.stopScanning = stopScanning;

            $scope.selectSource = function(source) {
                if(source == undefined) {
                    return;
                }

                endVideoStream();
                var videoElement = document.getElementById('checkInVideoElement');
                videoElement.src = null;


                var constraint = {video: {optional: [{sourceId: source.source.id}]}};

                navigator.getUserMedia(constraint, function(stream) {
                    $scope.stream = stream; // make stream available to console
                    videoElement.src = window.URL.createObjectURL(stream);
                    videoElement.play();
                    $timeout.cancel(timeoutPromise);
                    captureFrame();
                }, function() {
                    alert('error while loading camera');
                    $timeout.cancel(timeoutPromise);
                });
            };

            MediaStreamTrack.getSources(function(sources) {
                var videos = [];
                angular.forEach(sources, function(v,i) {
                    if(v.kind === 'video') {
                        videos.push({ source: v, label: (v.label || 'camera ' + i)});
                    }
                });
                $scope.$apply(function() {
                    $scope.videos = videos;
                });
            });
        }

        EventService.getEvent($stateParams.eventName).success(function(result) {
            $scope.event = result.event;
        });


        $scope.checkIn = function(ticket) {
            $scope.scanning.checkInInAction = true;
            CheckInService.checkIn($scope.event.id, ticket).success(function(result) {
                $scope.scanning.checkInInAction = false;
                $scope.scanning.scannedTicketInfo = result.ticket;
                $scope.scanning.scannedResult = result.result;


                if(result.ticket.status === 'CHECKED_IN') {
                    $scope.resetScanning();
                }
            });
        };

        $scope.resetScanning = function() {
            $scope.scanning = {visible: $scope.scanning, ticket: {}};
        };

        $scope.resetForm = function(ticket) {
            ticket.code = null;
            $scope.resetScanning();
        };

        $scope.confirmPayment = function() {
            $scope.scanning.confirmPaymentInAction = true;
            CheckInService.confirmPayment($scope.event.id, $scope.scanning.ticket).then(function() {
                CheckInService.getTicket($scope.event.id, $scope.scanning.ticket.code).success(function(result) {
                    $scope.scanning.scannedTicketInfo = result.ticket;
                    $scope.scanning.scannedResult = result.result;
                    $scope.scanning.confirmPaymentInAction = false;
                });
            });
        };

    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('PendingReservationsController', function($scope, EventService, $stateParams, $log, $window) {
        var getPendingPayments = function() {
            EventService.getPendingPayments($stateParams.eventName).success(function(data) {
                $scope.pendingReservations = data;
                $scope.loading = false;
            });
        };

        $scope.eventName = $stateParams.eventName;
        $scope.uploadSuccess = function(data) {
            $scope.results = data;
            getPendingPayments();
        };

        $scope.uploadUrl = '/admin/api/events/'+$stateParams.eventName+'/pending-payments/bulk-confirmation';

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            if(!$window.confirm('Do you really want to delete this reservation?')) {
                return;
            }
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
    });

    admin.controller('ComposeCustomMessage', function($scope, $stateParams, EventService, $modal, $state, $q) {


        $q.all([EventService.getSelectedLanguages($stateParams.eventName),
            EventService.getCategoriesContainingTickets($stateParams.eventName), EventService.getEvent($stateParams.eventName)])
        .then(function(results) {
                $scope.messages = _.map(results[0].data, function(r) {
                    return {
                        textExample: '{{organizationName}} <{{organizationEmail}}>',
                        subjectExample: 'An important message from {{eventName}}',
                        locale: r.language,
                        text: '',
                        subject: ''
                    };
                });
                $scope.fullName = 'John Doe';

                $scope.categories = results[1].data;
                $scope.categoryId = undefined;

                var eventDescriptor = results[2].data;
                $scope.organization = eventDescriptor.organization;
                $scope.eventName = eventDescriptor.event.shortName;
        });

        $scope.cancel = function() {
            $state.go('events.detail', {eventName: $stateParams.eventName});
        };


        $scope.showPreview = function(frm, eventName, categoryId, messages, categories) {
            if(!frm.$valid) {
                return;
            }
            var error = _.find(messages, function(m) {
                return _.trim(m.text) === '' || _.trim(m.subject) === '';
            });
            if(angular.isDefined(error)) {
                alert('please fill all the messages');
                return;
            }
            EventService.getMessagesPreview(eventName, categoryId, messages).success(function(result) {
                var preview = $modal.open({
                    size:'lg',
                    templateUrl:BASE_STATIC_URL + '/custom-message/preview.html',
                    backdrop: 'static',
                    controller: function($scope) {
                        if(angular.isDefined(categoryId)) {
                            var category = _.find(categories, function(c) {return c.id === categoryId});
                            $scope.categoryName = angular.isDefined(category) ? category.name : "";
                        }
                        $scope.messages = result.preview;
                        $scope.affectedUsers = result.affectedUsers;
                        $scope.eventName = eventName;
                        $scope.categoryId = categoryId;
                        $scope.cancel = function() {
                            $scope.$dismiss('canceled');
                        };
                        $scope.sendMessage = function(frm, eventName, categoryId, messages, affectedUsers) {
                            if(!frm.$valid) {
                                return;
                            }
                            if(affectedUsers === 0 && !confirm('No one will receive this message. Do you really want to continue?')) {
                                return;
                            }
                            EventService.sendMessages(eventName, categoryId, messages).success(function(result) {
                                alert(result + ' messages have been enqueued');
                                $scope.$close(true);
                            }).error(function(error) {
                                alert(error);
                            });
                        };
                    }
                });
            }).error(function(resp) {
                alert(resp);
            });
        };
    });

    admin.controller('ShowWaitingQueue', ['WaitingQueueService', '$stateParams', '$state', function(WaitingQueueService, $stateParams, $state) {
        var ctrl = this;
        this.loading = true;
        this.eventName = $stateParams.eventName;
        WaitingQueueService.loadAllSubscribers(this.eventName).success(function(result) {
            ctrl.subscriptions = result;
            ctrl.loading = false;
        });
    }]);

    admin.run(function($rootScope, PriceCalculator) {
        var calculateNetPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return numeral(0.0);
            }
            if(!event.vatIncluded) {
                return numeral(event.regularPrice);
            }
            return numeral(event.regularPrice).divide(numeral(1).add(numeral(event.vat).divide(100)));
        };

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['danger', 'warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = PriceCalculator.calcBarValue;

        $rootScope.calcCategoryPricePercent = PriceCalculator.calcCategoryPricePercent;

        $rootScope.calcCategoryPrice = PriceCalculator.calcCategoryPrice; 

        $rootScope.calcPercentage = PriceCalculator.calcPercentage;

        $rootScope.applyPercentage = PriceCalculator.applyPercentage;

        $rootScope.calculateTotalPrice = PriceCalculator.calculateTotalPrice;
    });

})();
