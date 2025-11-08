(function(){
    const params = new URLSearchParams(window.location.search);
    let oldToken = params.get('token');
    let accessToken = localStorage.getItem('eject_access_token');
    let refreshToken = localStorage.getItem('eject_refresh_token');

    const $login = document.getElementById('login');
    const $app = document.getElementById('app');
    const $who = document.getElementById('who');
    const $timer = document.getElementById('timer');
    const $fileTree = document.getElementById('fileTree');
    const $filesTable = document.getElementById('filesTable').querySelector('tbody');
    const $currentPath = document.getElementById('currentPath');
    const $fileInput = document.getElementById('fileInput');

    let currentPath = '';
    let allFiles = [];
    let allFolders = [];
    let trashFiles = [];
    let trashFolders = [];
    let isInTrash = false;
    let selectedMoveFile = null;
    let selectedTargetFolder = '';
    let confirmCallback = null;
    let refreshInterval = null;
    let uploadQueue = [];
    let isUploading = false;
    let selectedFiles = new Set();
    
    const $quotaProgress = document.getElementById('quotaProgress');
    const $quotaText = document.getElementById('quotaText');

    function showLogin() {
        window.location.href = '/login.html';
    }
    
    function showApp() {
        $login.classList.add('hidden');
        $app.classList.remove('hidden');
    }

    function showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.innerHTML = `
            <span style="flex: 1;">${message}</span>
            <button onclick="this.parentElement.remove()" style="background: none; border: none; color: inherit; font-size: 18px; cursor: pointer; margin-left: 10px; opacity: 0.7;" title="Закрыть">&times;</button>
        `;
        notification.style.display = 'flex';
        notification.style.alignItems = 'center';
        document.body.appendChild(notification);
        
        setTimeout(() => {
            if (notification.parentElement) {
                notification.remove();
            }
        }, 30000);
    }
    
    function showConfirm(message, callback) {
        document.getElementById('confirmMessage').textContent = message;
        confirmCallback = callback;
        document.getElementById('confirmModal').style.display = 'block';
    }
    
    window.closeConfirmModal = function() {
        document.getElementById('confirmModal').style.display = 'none';
        confirmCallback = null;
    };
    
    window.confirmAction = function() {
        if (confirmCallback) {
            confirmCallback();
        }
        closeConfirmModal();
    };

    window.moveFileDialog = function(fileId) {
        selectedMoveFile = fileId;
        const fileName = fileId.split('/').pop();
        
        document.getElementById('moveFileName').textContent = `Переместить файл: ${fileName}`;
        
        const folderTree = document.getElementById('folderTree');
        folderTree.innerHTML = '';
        
        // Корень
        const rootItem = document.createElement('div');
        rootItem.className = 'folder-item selected';
        rootItem.textContent = '🏠 Хранилище';
        rootItem.onclick = () => selectTargetFolder('', rootItem);
        folderTree.appendChild(rootItem);
        
        // Папки
        allFolders.forEach(folder => {
            const item = document.createElement('div');
            item.className = 'folder-item';
            const depth = folder.split('/').length - 1;
            item.style.paddingLeft = (20 + depth * 15) + 'px';
            item.textContent = '📁 ' + folder.split('/').pop();
            item.onclick = () => selectTargetFolder(folder, item);
            folderTree.appendChild(item);
        });
        
        selectedTargetFolder = '';
        document.getElementById('moveModal').style.display = 'block';
    };
    
    function selectTargetFolder(folder, element) {
        document.querySelectorAll('.folder-item').forEach(item => {
            item.classList.remove('selected');
        });
        element.classList.add('selected');
        selectedTargetFolder = folder;
    }
    
    window.closeMoveModal = function() {
        document.getElementById('moveModal').style.display = 'none';
        selectedMoveFile = null;
        selectedTargetFolder = '';
    };
    
    window.confirmMove = function() {
        if (selectedMoveFile) {
            moveFile(selectedMoveFile, selectedTargetFolder);
            closeMoveModal();
        }
    };

    function doValidate() {
        if (accessToken) {
            console.log('Validating JWT token');
            fetch(`/api/auth/validate?token=${encodeURIComponent(accessToken)}`)
                .then(r => r.json())
                .then(j => {
                    if (j.ok) {
                        $who.textContent = j.user;
                        
                        // Показываем кнопку админ-панели для админов
                        if (j.isAdmin) {
                            document.getElementById('adminPanelBtn').style.display = 'inline-block';
                        }
                        
                        showApp();
                        startTokenRefresh();
                        loadFiles();
                    } else {
                        tryRefreshToken();
                    }
                }).catch(e => {
                    console.error('Validation error:', e);
                    window.location.href = '/login.html';
                });
        } else if (oldToken) {
            convertOldToken();
        } else {
            showLogin();
        }
    }
    
    function convertOldToken() {
        console.log('Converting old token to JWT');
        fetch(`/auth/login?token=${encodeURIComponent(oldToken)}`, { method: 'POST' })
            .then(r => {
                if (!r.ok) throw new Error('Invalid token');
                return r.json();
            })
            .then(data => {
                accessToken = data.accessToken;
                refreshToken = data.refreshToken;
                localStorage.setItem('eject_access_token', accessToken);
                localStorage.setItem('eject_refresh_token', refreshToken);
                localStorage.removeItem('eject_token');
                
                $who.textContent = data.telegramId;
                showApp();
                startTokenRefresh();
                loadFiles();
            })
            .catch(e => {
                console.error('Token conversion error:', e);
                window.location.href = '/login.html';
            });
    }
    
    function tryRefreshToken() {
        if (!refreshToken) {
            showLogin();
            return Promise.reject('No refresh token');
        }
        
        return fetch(`/api/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`, { method: 'POST' })
            .then(r => {
                if (!r.ok) throw new Error('Refresh failed');
                return r.json();
            })
            .then(data => {
                accessToken = data.accessToken;
                localStorage.setItem('eject_access_token', accessToken);
                return data;
            })
            .catch(e => {
                console.error('Refresh error:', e);
                window.location.href = '/login.html';
                throw e;
            });
    }
    
    function startTokenRefresh() {
        if (refreshInterval) clearInterval(refreshInterval);
        
        // Обновляем токен каждые 10 минут
        refreshInterval = setInterval(() => {
            tryRefreshToken();
        }, 10 * 60 * 1000);
        
        // Скрываем таймер
        $timer.style.display = 'none';
    }

    function getAuthToken() {
        return accessToken;
    }

    function loadFiles() {
        const token = getAuthToken();
        Promise.all([
            fetch(`/api/files/list?token=${encodeURIComponent(token)}`).then(r => r.json()),
            fetch(`/api/files/folders?token=${encodeURIComponent(token)}`).then(r => r.json()),
            fetch(`/api/files/trash?token=${encodeURIComponent(token)}`).then(r => r.json()),
            fetch(`/api/files/trash/folders?token=${encodeURIComponent(token)}`).then(r => r.json()),
            fetch(`/api/files/quota?token=${encodeURIComponent(token)}`).then(r => r.json())
        ])
        .then(([files, folders, trash, trashFoldersData, quota]) => {
            allFiles = files;
            allFolders = folders;
            trashFiles = trash;
            trashFolders = trashFoldersData;
            updateQuotaDisplay(quota);
            buildFileTree();
            if (isInTrash) {
                showTrash();
            } else {
                showFilesInPath(currentPath);
            }
        })
        .catch(e => showNotification('Ошибка загрузки: ' + e.message, 'error'));
    }

    function buildFileTree() {
        $fileTree.innerHTML = '';
        
        // Корневая папка
        const rootItem = document.createElement('div');
        rootItem.className = 'tree-item root' + (!isInTrash && currentPath === '' ? ' selected' : '');
        rootItem.textContent = 'Хранилище';
        rootItem.onclick = () => selectPath('');
        $fileTree.appendChild(rootItem);

        // Строим иерархическое дерево
        const tree = {};
        allFolders.forEach(folder => {
            const parts = folder.split('/');
            let current = tree;
            let path = '';
            
            parts.forEach((part, index) => {
                path += (index > 0 ? '/' : '') + part;
                if (!current[part]) {
                    current[part] = { path: path, children: {} };
                }
                current = current[part].children;
            });
        });
        
        // Отображаем дерево
        renderTreeLevel(tree, $fileTree, 0);
        
        // Корзина в конце
        const trashItem = document.createElement('div');
        trashItem.className = 'tree-item' + (isInTrash && currentPath === '' ? ' selected' : '');
        trashItem.style.paddingLeft = '30px';
        trashItem.textContent = '🗑️ Корзина';
        trashItem.onclick = () => selectTrash();
        $fileTree.appendChild(trashItem);
        
        // Папки в корзине
        if (isInTrash && trashFolders.length > 0) {
            const trashTree = {};
            trashFolders.forEach(folder => {
                const parts = folder.split('/');
                let current = trashTree;
                let path = '';
                
                parts.forEach((part, index) => {
                    path += (index > 0 ? '/' : '') + part;
                    if (!current[part]) {
                        current[part] = { path: path, children: {} };
                    }
                    current = current[part].children;
                });
            });
            
            renderTreeLevel(trashTree, $fileTree, 2, true);
        }
    }
    
    function renderTreeLevel(level, container, depth, inTrash = false) {
        Object.keys(level).sort().forEach(name => {
            const node = level[name];
            const item = document.createElement('div');
            const isSelected = inTrash ? (isInTrash && currentPath === node.path) : (!isInTrash && currentPath === node.path);
            item.className = 'tree-item folder' + (isSelected ? ' selected' : '');
            item.style.paddingLeft = (30 + depth * 20) + 'px';
            item.textContent = name;
            item.onclick = () => inTrash ? selectTrashPath(node.path) : selectPath(node.path);
            container.appendChild(item);
            
            if (Object.keys(node.children).length > 0) {
                renderTreeLevel(node.children, container, depth + 1, inTrash);
            }
        });
    }

    function selectPath(path) {
        currentPath = path;
        isInTrash = false;
        clearSelection();
        updateToolbarButtons();
        buildFileTree();
        showFilesInPath(path);
    }
    
    function selectTrash() {
        isInTrash = true;
        currentPath = '';
        clearSelection();
        updateToolbarButtons();
        buildFileTree();
        showTrash();
    }
    
    function selectTrashPath(path) {
        isInTrash = true;
        currentPath = path;
        clearSelection();
        updateToolbarButtons();
        buildFileTree();
        showTrash();
    }

    function showFilesInPath(path) {
        $currentPath.textContent = path || 'Хранилище';
        
        // Фильтруем файлы для текущей папки
        const filesInPath = allFiles.filter(file => {
            const filePath = file.id.includes('/') ? file.id.substring(0, file.id.lastIndexOf('/')) : '';
            return filePath === path;
        });
        
        // Подпапки в текущей папке
        const subfolders = allFolders.filter(folder => {
            if (path === '') {
                return !folder.includes('/');
            } else {
                return folder.startsWith(path + '/') && 
                       folder.split('/').length === path.split('/').length + 1;
            }
        });

        $filesTable.innerHTML = '';
        
        // Кнопка "Назад" (если не в корне)
        if (path !== '') {
            const row = $filesTable.insertRow();
            const parentPath = path.includes('/') ? path.substring(0, path.lastIndexOf('/')) : '';
            row.insertCell(); // Пустая ячейка для чекбокса
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ..';
            cell.onclick = () => selectPath(parentPath);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
        }
        
        // Подпапки
        subfolders.forEach(folder => {
            const row = $filesTable.insertRow();
            const folderName = folder.split('/').pop();
            row.insertCell(); // Пустая ячейка для чекбокса
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ' + folderName;
            cell.onclick = () => selectPath(folder);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            
            const actionsCell = row.insertCell();
            actionsCell.innerHTML = `
                <div class="action-buttons">
                    <button onclick="event.stopPropagation(); renameFolder('${folder}')" class="secondary" title="Переименовать">✏️</button>
                    <button onclick="event.stopPropagation(); deleteFolder('${folder}')" class="danger" title="Удалить папку">🗑️</button>
                </div>
            `;
        });
        
        // Файлы
        filesInPath.forEach(file => {
            const row = $filesTable.insertRow();
            row.className = 'file-row';
            row.dataset.fileId = file.id;
            const fileName = file.id.includes('/') ? file.id.split('/').pop() : file.id;
            const fileSize = formatFileSize(file.size);
            const fileDate = new Date(file.uploadedAt).toLocaleString();
            
            // Чекбокс
            const checkboxCell = row.insertCell();
            checkboxCell.innerHTML = `<input type="checkbox" class="file-checkbox" onchange="toggleFileSelection('${file.id}', this)">`;
            
            // Название файла
            const nameCell = row.insertCell();
            nameCell.textContent = `📄 ${fileName}`;
            
            // Размер
            const sizeCell = row.insertCell();
            sizeCell.textContent = fileSize;
            
            // Дата
            const dateCell = row.insertCell();
            dateCell.textContent = fileDate;
            
            // Ссылка
            const shareCell = row.insertCell();
            if (file.shared && file.shareExpiresAt) {
                const timeLeft = getTimeUntilExpiry(file.shareExpiresAt);
                shareCell.innerHTML = `
                    <span style="cursor: pointer; color: #007acc;" onclick="copyExistingShare('${file.id}')" title="Кликните для копирования">🔗 ${timeLeft}</span>
                    <button onclick="deleteShareLink('${file.id}')" style="margin-left: 5px; font-size: 12px; padding: 2px 6px;">×</button>
                `;
            } else {
                shareCell.textContent = '-';
            }
            
            // Действия
            const actionsCell = row.insertCell();
            actionsCell.innerHTML = `
                <div class="action-buttons">
                    <button onclick="downloadFile('${file.id}')" class="primary" title="Скачать">⬇️</button>
                    <button onclick="shareFile('${file.id}')" class="secondary" title="Поделиться">🔗</button>
                    <button onclick="renameFile('${file.id}')" class="secondary" title="Переименовать">✏️</button>
                    <button onclick="moveFileDialog('${file.id}')" class="secondary" title="Переместить">📁</button>
                    <button onclick="deleteFile('${file.id}')" class="danger" title="Удалить">🗑️</button>
                </div>
            `;
        });
        
        updateBulkActionsVisibility();
    }

    function formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    function updateToolbarButtons() {
        const uploadBtn = document.getElementById('uploadBtn');
        const createFolderBtn = document.getElementById('createFolderBtn');
        
        if (isInTrash) {
            uploadBtn.style.display = 'none';
            createFolderBtn.style.display = 'none';
        } else {
            uploadBtn.style.display = 'inline-block';
            createFolderBtn.style.display = 'inline-block';
        }
    }

    window.uploadFiles = function() {
        if (isInTrash) return;
        $fileInput.click();
    };

    $fileInput.addEventListener('change', async function() {
        const files = this.files;
        if (files.length === 0) return;

        // Получаем текущую квоту
        const token = getAuthToken();
        let quotaInfo;
        try {
            const response = await fetch(`/api/files/quota?token=${encodeURIComponent(token)}`);
            quotaInfo = await response.json();
        } catch (e) {
            showNotification('Ошибка получения информации о квоте', 'error');
            this.value = '';
            return;
        }

        // Проверяем размеры файлов
        const filesToUpload = [];
        const rejectedFiles = [];
        let totalSize = 0;
        
        Array.from(files).forEach(file => {
            totalSize += file.size;
            if (quotaInfo.remaining >= totalSize) {
                filesToUpload.push(file);
            } else {
                rejectedFiles.push(file);
            }
        });
        
        // Показываем предупреждения о отклоненных файлах
        if (rejectedFiles.length > 0) {
            const rejectedNames = rejectedFiles.map(f => f.name).join(', ');
            showNotification(`Файлы не загружены (недостаточно места): ${rejectedNames}`, 'warning');
        }
        
        // Добавляем только подходящие файлы в очередь
        if (filesToUpload.length > 0) {
            filesToUpload.forEach(file => {
                uploadQueue.push({ file, path: currentPath });
            });
            
            // Показываем/обновляем прогресс
            if (!document.getElementById('uploadProgress')) {
                showUploadProgress();
            } else {
                updateUploadProgressDisplay();
            }
            
            // Запускаем обработку очереди если не загружаем
            if (!isUploading) {
                processUploadQueue();
            }
        }
        
        this.value = ''; // Очищаем input
    });
    
    function processUploadQueue() {
        if (uploadQueue.length === 0) {
            isUploading = false;
            window.currentUploadFile = null;
            setTimeout(() => {
                hideUploadProgress();
                loadFiles();
            }, 2000);
            return;
        }
        
        isUploading = true;
        const { file, path } = uploadQueue.shift();
        window.currentUploadFile = file.name;
        
        if (!window.uploadedFiles) window.uploadedFiles = [];
        updateUploadProgressDisplay();
        
        // Обновляем токен перед каждой загрузкой
        tryRefreshToken().then(() => {
            const fd = new FormData();
            fd.append('file', file);
            fd.append('token', getAuthToken());
            if (path) fd.append('path', path);

            // Получаем таймаут из конфига
            fetch(`/api/files/config/upload-timeout?token=${encodeURIComponent(getAuthToken())}`)
                .then(r => r.json())
                .then(config => {
                    startUpload(config.timeout, fd);
                })
                .catch(() => {
                    startUpload(10800000, fd); // fallback 3 часа
                });
        }).catch(() => {
            // Ошибка обновления токена
            showNotification(`Ошибка аутентификации при загрузке ${file.name}`, 'error');
            processUploadQueue();
        });
            
        function startUpload(timeout, fd) {
            const xhr = new XMLHttpRequest();
            let startTime = Date.now();
            let lastLoaded = 0;
            let lastTime = startTime;
            let tokenRefreshInterval;
            
            // Обновляем токен каждые 5 минут во время загрузки
            tokenRefreshInterval = setInterval(() => {
                tryRefreshToken();
            }, 5 * 60 * 1000);
            
            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const now = Date.now();
                    const timeDiff = (now - lastTime) / 1000;
                    const loadedDiff = e.loaded - lastLoaded;
                    
                    let speed = 0;
                    if (timeDiff > 0.5) {
                        speed = loadedDiff / timeDiff;
                        lastTime = now;
                        lastLoaded = e.loaded;
                    }
                    
                    const percentComplete = (e.loaded / e.total) * 100;
                    updateUploadProgress(percentComplete, e.loaded, e.total, speed);
                }
            });
            
            xhr.onload = function() {
                clearInterval(tokenRefreshInterval);
                if (xhr.status === 200) {
                    try {
                        const response = JSON.parse(xhr.responseText);
                        if (response.renamed) {
                            showNotification(`Файл "${response.originalName}" переименован в "${response.newName}"`, 'warning');
                        }
                    } catch (e) {
                        // Игнорируем ошибки парсинга
                    }
                    window.uploadedFiles.push(file.name);
                    processUploadQueue();
                } else {
                    showNotification(`Ошибка загрузки ${file.name}: ${xhr.responseText}`, 'error');
                    processUploadQueue();
                }
            };
            
            xhr.onerror = function() {
                clearInterval(tokenRefreshInterval);
                showNotification(`Ошибка загрузки ${file.name}: Соединение прервано`, 'error');
                processUploadQueue();
            };
            
            xhr.ontimeout = function() {
                clearInterval(tokenRefreshInterval);
                showNotification(`Ошибка загрузки ${file.name}: Превышено время ожидания`, 'error');
                processUploadQueue();
            };
            
            xhr.timeout = timeout;
            xhr.open('POST', '/api/files/upload');
            xhr.send(fd);
        }
    }
    
    function showUploadProgress() {
        let progressDiv = document.getElementById('uploadProgress');
        if (!progressDiv) {
            progressDiv = document.createElement('div');
            progressDiv.id = 'uploadProgress';
            progressDiv.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                background: white;
                border: 1px solid #ccc;
                border-radius: 5px;
                padding: 15px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                z-index: 1000;
                min-width: 400px;
                max-height: 500px;
                overflow-y: auto;
            `;
            document.body.appendChild(progressDiv);
        }
        updateUploadProgressDisplay();
    }
    
    function updateUploadProgressDisplay() {
        const progressDiv = document.getElementById('uploadProgress');
        if (!progressDiv) return;
        
        let html = `<div style="margin-bottom: 10px; font-weight: bold;">Загрузка файлов</div>`;
        
        // Загруженные файлы
        (window.uploadedFiles || []).forEach(file => {
            html += `<div style="margin: 5px 0; font-size: 12px; display: flex; align-items: center;">
                <span style="color: green; margin-right: 8px;">✓</span>
                <span style="flex: 1; word-break: break-all;">${file}</span>
            </div>`;
        });
        
        // Текущий файл
        if (isUploading && window.currentUploadFile) {
            html += `<div style="margin: 5px 0; font-size: 12px; display: flex; align-items: center;">
                <span style="color: #007acc; margin-right: 8px;">🔄</span>
                <span style="flex: 1; word-break: break-all;">${window.currentUploadFile}</span>
            </div>`;
            html += `<div style="background: #f0f0f0; border-radius: 3px; overflow: hidden; margin: 5px 0;">
                <div id="uploadProgressBar" style="background: #007acc; height: 20px; width: 0%; transition: width 0.3s;"></div>
            </div>
            <div id="uploadProgressText" style="text-align: center; margin-top: 5px; font-size: 12px;">0%</div>
            <div id="uploadSpeedText" style="text-align: center; margin-top: 3px; font-size: 11px; color: #666;"></div>
            <div id="uploadSizeText" style="text-align: center; margin-top: 3px; font-size: 11px; color: #666;"></div>`;
        }
        
        // Файлы в очереди
        uploadQueue.forEach(item => {
            html += `<div style="margin: 5px 0; font-size: 12px; display: flex; align-items: center;">
                <span style="color: #666; margin-right: 8px;">⏳</span>
                <span style="flex: 1; word-break: break-all;">${item.file.name}</span>
            </div>`;
        });
        
        progressDiv.innerHTML = html;
    }
    
    function updateUploadProgress(percent, loaded, total, speed) {
        const progressBar = document.getElementById('uploadProgressBar');
        const progressText = document.getElementById('uploadProgressText');
        const speedText = document.getElementById('uploadSpeedText');
        const sizeText = document.getElementById('uploadSizeText');
        
        if (progressBar && progressText) {
            progressBar.style.width = percent + '%';
            progressText.textContent = Math.round(percent) + '%';
            
            if (speedText && speed > 0) {
                let speedStr = '';
                if (speed > 1024 * 1024) {
                    speedStr = (speed / 1024 / 1024).toFixed(1) + ' MB/s';
                } else if (speed > 1024) {
                    speedStr = (speed / 1024).toFixed(1) + ' KB/s';
                } else {
                    speedStr = speed.toFixed(0) + ' B/s';
                }
                
                // Расчет оставшегося времени
                const remaining = total - loaded;
                const timeLeft = remaining / speed;
                let timeStr = '';
                
                if (timeLeft > 3600) {
                    const hours = Math.floor(timeLeft / 3600);
                    const minutes = Math.floor((timeLeft % 3600) / 60);
                    timeStr = ` (осталось ${hours} ч. ${minutes} мин.)`;
                } else if (timeLeft > 60) {
                    const minutes = Math.floor(timeLeft / 60);
                    const seconds = Math.floor(timeLeft % 60);
                    timeStr = ` (осталось ${minutes} м. ${seconds} сек.)`;
                } else if (timeLeft > 0) {
                    timeStr = ` (осталось ${Math.ceil(timeLeft)} сек.)`;
                }
                
                speedText.textContent = speedStr + timeStr;
            }
            
            if (sizeText && loaded !== undefined && total !== undefined) {
                sizeText.textContent = `${formatFileSize(loaded)} / ${formatFileSize(total)}`;
            }
        }
    }
    
    function hideUploadProgress() {
        const progressDiv = document.getElementById('uploadProgress');
        if (progressDiv) {
            progressDiv.remove();
        }
        window.uploadedFiles = [];
        window.currentUploadFile = null;
    }
    
    function getTimeUntilExpiry(expiresAt) {
        const now = new Date();
        const expiry = new Date(expiresAt);
        const diffMs = expiry - now;
        
        if (diffMs <= 0) {
            return 'истекла';
        }
        
        const hours = Math.floor(diffMs / (1000 * 60 * 60));
        const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
        
        if (hours > 0) {
            return `${hours}ч ${minutes}м`;
        } else {
            return `${minutes}м`;
        }
    }

    let inputCallback = null;
    
    function showInput(title, message, callback, defaultValue = '') {
        document.getElementById('inputTitle').textContent = title;
        document.getElementById('inputMessage').textContent = message;
        document.getElementById('inputField').value = defaultValue;
        inputCallback = callback;
        document.getElementById('inputModal').style.display = 'block';
        document.getElementById('inputField').focus();
        document.getElementById('inputField').select();
    }
    
    window.closeInputModal = function() {
        document.getElementById('inputModal').style.display = 'none';
        inputCallback = null;
    };
    
    window.confirmInput = function() {
        const value = document.getElementById('inputField').value.trim();
        if (inputCallback && value) {
            inputCallback(value);
        }
        closeInputModal();
    };
    
    // Enter для подтверждения
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && document.getElementById('inputModal').style.display === 'block') {
            confirmInput();
        }
    });

    window.createFolder = function() {
        if (isInTrash) return;
        showInput('Создать папку', 'Введите имя папки:', (folderName) => {
            // Валидация имени папки
            if (/[<>:"/\\|?*]/.test(folderName)) {
                showNotification('Имя папки содержит запрещенные символы', 'error');
                return;
            }

            const folderPath = currentPath ? `${currentPath}/${folderName}` : folderName;
            const token = getAuthToken();
            
            fetch(`/api/files/mkdir?token=${encodeURIComponent(token)}&path=${encodeURIComponent(folderPath)}`, {
                method: 'POST'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка создания папки');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };
    
    window.deleteFolder = function(folderPath) {
        const folderName = folderPath.split('/').pop();
        showConfirm(`Переместить папку "${folderName}" в корзину?`, () => {
            const token = getAuthToken();
            fetch(`/api/files/folder?path=${encodeURIComponent(folderPath)}&token=${encodeURIComponent(token)}`, {
                method: 'DELETE'
            })
            .then(r => {
                if (!r.ok) return r.text().then(t => { throw new Error(t); });
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };

    window.downloadFile = function(fileId) {
        const token = getAuthToken();
        window.open(`/api/files/download?fileId=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`);
    };

    window.shareFile = function(fileId) {
        const token = getAuthToken();
        fetch(`/api/files/share?fileId=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(async r => {
            if (!r.ok) {
                const text = await r.text();
                try {
                    const errorData = JSON.parse(text);
                    throw new Error(errorData.error || 'Ошибка сервера');
                } catch {
                    throw new Error(text || 'Ошибка сервера');
                }
            }
            return r.json();
        })
        .then(data => {
            const shareUrl = window.location.origin + data.shareUrl;
            navigator.clipboard.writeText(shareUrl).then(() => {
                showNotification('Ссылка скопирована в буфер обмена', 'success');
                loadFiles(); // Обновляем список для отображения ссылки
            }).catch(() => {
                prompt('Ссылка для скачивания:', shareUrl);
                loadFiles();
            });
        })
        .catch(e => showNotification('Ошибка создания ссылки: ' + e.message, 'error'));
    };
    
    window.copyExistingShare = function(fileId) {
        const token = getAuthToken();
        fetch(`/api/files/share?fileId=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(async r => {
            if (!r.ok) {
                const text = await r.text();
                try {
                    const errorData = JSON.parse(text);
                    throw new Error(errorData.error || 'Ошибка сервера');
                } catch {
                    throw new Error(text || 'Ошибка сервера');
                }
            }
            return r.json();
        })
        .then(data => {
            const shareUrl = window.location.origin + data.shareUrl;
            navigator.clipboard.writeText(shareUrl).then(() => {
                showNotification('Ссылка скопирована в буфер обмена', 'success');
            }).catch(() => {
                prompt('Ссылка для скачивания:', shareUrl);
            });
        })
        .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
    };
    
    window.deleteShareLink = function(fileId) {
        showConfirm('Удалить ссылку на файл?', () => {
            const token = getAuthToken();
            fetch(`/api/files/share?fileId=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
                method: 'DELETE'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка удаления ссылки');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };

    window.deleteFile = function(fileId) {
        const fileName = fileId.split('/').pop();
        showConfirm(`Переместить файл "${fileName}" в корзину?`, () => {
            const token = getAuthToken();
            fetch(`/api/files/delete?id=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
                method: 'DELETE'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка удаления');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };

    function moveFile(fileId, targetFolder) {
        const token = getAuthToken();
        fetch(`/api/files/move?fileId=${encodeURIComponent(fileId)}&targetFolder=${encodeURIComponent(targetFolder)}&token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(async r => {
            if (!r.ok) {
                const text = await r.text();
                try {
                    const errorData = JSON.parse(text);
                    throw new Error(errorData.error || 'Ошибка перемещения');
                } catch {
                    throw new Error(text || 'Ошибка перемещения');
                }
            }
            return r.json();
        })
        .then(data => {
            if (data.renamed) {
                showNotification(`Файл перемещен и переименован в "${data.newName}"`, 'warning');
            } else {
                showNotification('Файл успешно перемещен', 'success');
            }
            loadFiles();
        })
        .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
    }

    function logout() {
        if (refreshToken) {
            fetch(`/api/auth/logout?refreshToken=${encodeURIComponent(refreshToken)}`, { method: 'POST' })
                .catch(() => {}); // Игнорируем ошибки
        }
        
        if (refreshInterval) {
            clearInterval(refreshInterval);
            refreshInterval = null;
        }
        
        localStorage.removeItem('eject_access_token');
        localStorage.removeItem('eject_refresh_token');
        accessToken = null;
        refreshToken = null;
        
        window.location.href = '/login.html';
    }
    
    window.logout = logout;
    
    window.openAdminPanel = function() {
        window.location.href = '/admin-panel.html';
    };

    function showTrash() {
        $currentPath.textContent = currentPath ? `🗑️ Корзина / ${currentPath}` : '🗑️ Корзина';
        $filesTable.innerHTML = '';
        
        // Фильтруем элементы для текущей папки корзины
        const itemsInPath = trashFiles.filter(item => {
            const itemPath = item.id.includes('/') ? item.id.substring(0, item.id.lastIndexOf('/')) : '';
            return itemPath === currentPath;
        });
        
        // Подпапки в текущей папке корзины
        const subfolders = trashFolders.filter(folder => {
            if (currentPath === '') {
                return !folder.includes('/');
            } else {
                return folder.startsWith(currentPath + '/') && 
                       folder.split('/').length === currentPath.split('/').length + 1;
            }
        });
        
        // Кнопка очистить корзину (только в корне)
        if (currentPath === '' && (itemsInPath.length > 0 || subfolders.length > 0)) {
            const row = $filesTable.insertRow();
            row.style.backgroundColor = '#fff3cd';
            row.innerHTML = `
                <td></td>
                <td colspan="4" style="text-align: center; font-weight: bold;">
                    В корзине ${trashFiles.length} элементов
                </td>
                <td>
                    <button onclick="clearTrash()" class="danger" title="Очистить корзину">🗑️ Очистить все</button>
                </td>
            `;
        }
        
        // Кнопка "Назад" (если не в корне корзины)
        if (currentPath !== '') {
            const row = $filesTable.insertRow();
            const parentPath = currentPath.includes('/') ? currentPath.substring(0, currentPath.lastIndexOf('/')) : '';
            row.insertCell(); // Пустая ячейка для чекбокса
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ..';
            cell.onclick = () => selectTrashPath(parentPath);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
        }
        
        // Подпапки
        subfolders.forEach(folder => {
            const row = $filesTable.insertRow();
            const folderName = folder.split('/').pop();
            row.insertCell(); // Пустая ячейка для чекбокса
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ' + folderName;
            cell.onclick = () => selectTrashPath(folder);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            
            const actionsCell = row.insertCell();
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'action-buttons';
            
            const restoreBtn = document.createElement('button');
            restoreBtn.innerHTML = '↩️';
            restoreBtn.title = 'Восстановить';
            restoreBtn.className = 'success';
            restoreBtn.onclick = (e) => {
                e.stopPropagation();
                restoreFromTrash(folder);
            };
            
            const deleteBtn = document.createElement('button');
            deleteBtn.innerHTML = '🗑️';
            deleteBtn.title = 'Удалить навсегда';
            deleteBtn.className = 'danger';
            deleteBtn.onclick = (e) => {
                e.stopPropagation();
                deleteFromTrash(folder);
            };
            
            actionsDiv.appendChild(restoreBtn);
            actionsDiv.appendChild(deleteBtn);
            actionsCell.appendChild(actionsDiv);
        });
        
        // Файлы
        itemsInPath.forEach(item => {
            const row = $filesTable.insertRow();
            const itemName = item.id.includes('/') ? item.id.split('/').pop() : item.id;

            const itemSize = (item.sizeBytes !== undefined && item.sizeBytes !== null) ? formatFileSize(item.sizeBytes) : '-';
            const itemDate = new Date(item.uploadedAt).toLocaleString();
            
            row.innerHTML = `
                <td></td>
                <td>📄 ${itemName}</td>
                <td>${itemSize}</td>
                <td>${itemDate}</td>
                <td>-</td>
                <td>
                    <div class="action-buttons">
                        <button onclick="restoreFromTrash('${item.id}')" class="success" title="Восстановить">↩️</button>
                        <button onclick="deleteFromTrash('${item.id}')" class="danger" title="Удалить навсегда">🗑️</button>
                    </div>
                </td>
            `;
        });
        
        if (itemsInPath.length === 0 && subfolders.length === 0 && currentPath === '') {
            const row = $filesTable.insertRow();
            row.innerHTML = `
                <td colspan="6" style="text-align: center; color: #666; font-style: italic;">
                    Корзина пуста
                </td>
            `;
        }
    }
    
    window.clearTrash = function() {
        showConfirm('Очистить корзину? Все файлы будут удалены навсегда!', () => {
            const token = getAuthToken();
            fetch(`/api/files/trash/clear?token=${encodeURIComponent(token)}`, {
                method: 'DELETE'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка очистки корзины');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };
    
    window.deleteFromTrash = function(itemId) {
        showConfirm('Удалить навсегда? Это действие нельзя отменить!', () => {
            const token = getAuthToken();
            fetch(`/api/files/trash/${encodeURIComponent(itemId)}?token=${encodeURIComponent(token)}`, {
                method: 'DELETE'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка удаления');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };
    
    window.restoreFromTrash = function(itemId) {
        const itemName = itemId.split('/').pop();
        showConfirm(`Восстановить "${itemName}"?`, () => {
            const token = getAuthToken();
            fetch(`/api/files/trash/restore/${encodeURIComponent(itemId)}?token=${encodeURIComponent(token)}`, {
                method: 'POST'
            })
            .then(r => {
                if (!r.ok) throw new Error('Ошибка восстановления');
                loadFiles();
            })
            .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
        });
    };
    
    function validateFileName(name) {
        if (!name || name.trim().length === 0) {
            throw new Error('Имя не может быть пустым');
        }
        
        const trimmed = name.trim();
        if (trimmed.length > 255) {
            throw new Error('Имя слишком длинное (максимум 255 символов)');
        }
        
        if (/[<>:"/\\|?*]/.test(trimmed)) {
            throw new Error('Имя содержит запрещенные символы: < > : " / \\ | ? *');
        }
        
        if (trimmed === '.' || trimmed === '..') {
            throw new Error('Недопустимое имя');
        }
        
        const reserved = ['CON', 'PRN', 'AUX', 'NUL', 'COM1', 'COM2', 'COM3', 'COM4', 'COM5', 'COM6', 'COM7', 'COM8', 'COM9', 'LPT1', 'LPT2', 'LPT3', 'LPT4', 'LPT5', 'LPT6', 'LPT7', 'LPT8', 'LPT9'];
        const upperName = trimmed.toUpperCase();
        for (const res of reserved) {
            if (upperName === res || upperName.startsWith(res + '.')) {
                throw new Error(`Зарезервированное имя: ${res}`);
            }
        }
        
        return trimmed;
    }
    
    window.renameFile = function(fileId) {
        const fileName = fileId.includes('/') ? fileId.split('/').pop() : fileId;
        showInput('Переименовать файл', `Новое имя для "${fileName}":`, (newName) => {
            try {
                const validName = validateFileName(newName);
                const token = getAuthToken();
                
                fetch(`/api/files/rename?fileId=${encodeURIComponent(fileId)}&newName=${encodeURIComponent(validName)}&token=${encodeURIComponent(token)}`, {
                    method: 'POST'
                })
                .then(async r => {
                    if (!r.ok) {
                        const text = await r.text();
                        try {
                            const errorData = JSON.parse(text);
                            throw new Error(errorData.error || 'Ошибка переименования');
                        } catch {
                            throw new Error(text || 'Ошибка переименования');
                        }
                    }
                    showNotification('Файл успешно переименован', 'success');
                    loadFiles();
                })
                .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
            } catch (e) {
                showNotification(e.message, 'error');
            }
        }, fileName);
    };
    
    window.renameFolder = function(folderPath) {
        const folderName = folderPath.split('/').pop();
        showInput('Переименовать папку', `Новое имя для "${folderName}":`, (newName) => {
            try {
                const validName = validateFileName(newName);
                const token = getAuthToken();
                
                fetch(`/api/files/rename-folder?folderPath=${encodeURIComponent(folderPath)}&newName=${encodeURIComponent(validName)}&token=${encodeURIComponent(token)}`, {
                    method: 'POST'
                })
                .then(async r => {
                    if (!r.ok) {
                        const text = await r.text();
                        try {
                            const errorData = JSON.parse(text);
                            throw new Error(errorData.error || 'Ошибка переименования');
                        } catch {
                            throw new Error(text || 'Ошибка переименования');
                        }
                    }
                    showNotification('Папка успешно переименована', 'success');
                    loadFiles();
                })
                .catch(e => showNotification('Ошибка: ' + e.message, 'error'));
            } catch (e) {
                showNotification(e.message, 'error');
            }
        }, folderName);
    };
    
    function updateQuotaDisplay(quota) {
        const percentage = Math.min(100, quota.percentage);
        const usedGB = (quota.used / 1024 / 1024 / 1024).toFixed(2);
        const totalGB = (quota.quota / 1024 / 1024 / 1024).toFixed(2);
        const remainingGB = (quota.remaining / 1024 / 1024 / 1024).toFixed(2);
        
        $quotaProgress.style.width = percentage + '%';
        $quotaText.textContent = `Осталось: ${remainingGB} GB / ${totalGB} GB`;
        
        // Цвет прогрессбара в зависимости от заполненности
        if (percentage < 70) {
            $quotaProgress.style.backgroundColor = '#28a745';
        } else if (percentage < 90) {
            $quotaProgress.style.backgroundColor = '#ffc107';
        } else {
            $quotaProgress.style.backgroundColor = '#dc3545';
        }
    }

    // Функции множественного выделения
    window.toggleSelectAll = function() {
        const selectAllCheckbox = document.getElementById('selectAll');
        const fileCheckboxes = document.querySelectorAll('.file-checkbox');
        
        fileCheckboxes.forEach(checkbox => {
            checkbox.checked = selectAllCheckbox.checked;
            toggleFileSelection(checkbox.closest('tr').dataset.fileId, checkbox);
        });
    };
    
    window.toggleFileSelection = function(fileId, checkbox) {
        const row = checkbox.closest('tr');
        
        if (checkbox.checked) {
            selectedFiles.add(fileId);
            row.classList.add('selected');
        } else {
            selectedFiles.delete(fileId);
            row.classList.remove('selected');
        }
        
        updateBulkActionsVisibility();
        updateSelectAllCheckbox();
    };
    
    function updateBulkActionsVisibility() {
        const bulkActions = document.getElementById('bulkActions');
        const selectedCount = document.getElementById('selectedCount');
        
        if (selectedFiles.size > 0) {
            bulkActions.style.display = 'flex';
            selectedCount.textContent = selectedFiles.size;
        } else {
            bulkActions.style.display = 'none';
        }
    }
    
    function updateSelectAllCheckbox() {
        const selectAllCheckbox = document.getElementById('selectAll');
        const fileCheckboxes = document.querySelectorAll('.file-checkbox');
        const checkedCheckboxes = document.querySelectorAll('.file-checkbox:checked');
        
        if (fileCheckboxes.length === 0) {
            selectAllCheckbox.indeterminate = false;
            selectAllCheckbox.checked = false;
        } else if (checkedCheckboxes.length === fileCheckboxes.length) {
            selectAllCheckbox.indeterminate = false;
            selectAllCheckbox.checked = true;
        } else if (checkedCheckboxes.length > 0) {
            selectAllCheckbox.indeterminate = true;
        } else {
            selectAllCheckbox.indeterminate = false;
            selectAllCheckbox.checked = false;
        }
    }
    
    window.clearSelection = function() {
        selectedFiles.clear();
        document.querySelectorAll('.file-checkbox').forEach(checkbox => {
            checkbox.checked = false;
        });
        document.querySelectorAll('.file-row').forEach(row => {
            row.classList.remove('selected');
        });
        updateBulkActionsVisibility();
        updateSelectAllCheckbox();
    };
    
    window.bulkMove = function() {
        if (selectedFiles.size === 0) return;
        
        document.getElementById('moveFileName').textContent = `Переместить ${selectedFiles.size} файлов:`;
        
        const folderTree = document.getElementById('folderTree');
        folderTree.innerHTML = '';
        
        // Корень
        const rootItem = document.createElement('div');
        rootItem.className = 'folder-item selected';
        rootItem.textContent = '🏠 Хранилище';
        rootItem.onclick = () => selectTargetFolder('', rootItem);
        folderTree.appendChild(rootItem);
        
        // Папки
        allFolders.forEach(folder => {
            const item = document.createElement('div');
            item.className = 'folder-item';
            const depth = folder.split('/').length - 1;
            item.style.paddingLeft = (20 + depth * 15) + 'px';
            item.textContent = '📁 ' + folder.split('/').pop();
            item.onclick = () => selectTargetFolder(folder, item);
            folderTree.appendChild(item);
        });
        
        selectedTargetFolder = '';
        selectedMoveFile = 'bulk';
        document.getElementById('moveModal').style.display = 'block';
    };
    
    window.bulkDelete = function() {
        if (selectedFiles.size === 0) return;
        
        showConfirm(`Переместить ${selectedFiles.size} файлов в корзину?`, () => {
            const token = getAuthToken();
            const promises = Array.from(selectedFiles).map(fileId => 
                fetch(`/api/files/delete?id=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
                    method: 'DELETE'
                })
            );
            
            Promise.all(promises.map(p => p.catch(e => ({ error: true, message: e.message }))))
                .then(responses => {
                    const failed = responses.filter(r => !r.ok && r.error !== true);
                    const alreadyInTrash = responses.filter(r => r.error && r.message && r.message.includes('уже есть в корзине'));
                    const success = responses.filter(r => r.ok);
                    
                    if (success.length > 0) {
                        showNotification(`${success.length} файлов перемещено в корзину`, 'success');
                    }
                    
                    if (alreadyInTrash.length > 0) {
                        showNotification(`${alreadyInTrash.length} файлов не удалено - уже есть в корзине`, 'warning');
                    }
                    
                    if (failed.length > 0) {
                        showNotification(`Ошибка удаления ${failed.length} файлов`, 'error');
                    }
                    
                    clearSelection();
                    loadFiles();
                })
                .catch(e => {
                    showNotification('Ошибка: ' + e.message, 'error');
                });
        });
    };
    
    // Обновляем confirmMove для поддержки группового перемещения
    const originalConfirmMove = window.confirmMove;
    window.confirmMove = function() {
        if (selectedMoveFile === 'bulk') {
            if (selectedFiles.size === 0) return;
            
            const token = getAuthToken();
            const promises = Array.from(selectedFiles).map(fileId => 
                fetch(`/api/files/move?fileId=${encodeURIComponent(fileId)}&targetFolder=${encodeURIComponent(selectedTargetFolder)}&token=${encodeURIComponent(token)}`, {
                    method: 'POST'
                })
            );
            
            Promise.all(promises.map(p => p.then(r => r.json().catch(() => ({}))).catch(() => ({ error: true }))))
                .then(results => {
                    const failed = results.filter(r => r.error);
                    const renamed = results.filter(r => r.renamed);
                    
                    if (failed.length > 0) {
                        showNotification(`Ошибка перемещения ${failed.length} файлов`, 'error');
                    } else if (renamed.length > 0) {
                        showNotification(`${selectedFiles.size} файлов перемещено, ${renamed.length} переименовано`, 'warning');
                    } else {
                        showNotification(`${selectedFiles.size} файлов успешно перемещено`, 'success');
                    }
                    clearSelection();
                    loadFiles();
                })
                .catch(e => {
                    showNotification('Ошибка: ' + e.message, 'error');
                });
            
            closeMoveModal();
        } else {
            originalConfirmMove();
        }
    };
    
    // Проверяем аутентификацию
    if (!accessToken && !oldToken) {
        window.location.href = '/login.html';
        return;
    }
    
    doValidate();
})();