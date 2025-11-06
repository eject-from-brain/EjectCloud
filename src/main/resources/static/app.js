(function(){
    const params = new URLSearchParams(window.location.search);
    let token = params.get('token') || localStorage.getItem('eject_token');

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
    let inactivitySeconds = 1800; // 30 минут
    let lastActivity = Date.now();
    
    const $quotaProgress = document.getElementById('quotaProgress');
    const $quotaText = document.getElementById('quotaText');

    function showLogin() {
        $login.classList.remove('hidden');
        $app.classList.add('hidden');
    }
    
    function showApp() {
        $login.classList.add('hidden');
        $app.classList.remove('hidden');
    }

    function doValidate() {
        console.log('Validating token:', token);
        fetch(`/auth/validate?token=${encodeURIComponent(token)}`)
            .then(r => {
                console.log('Response status:', r.status);
                return r.json();
            })
            .then(j => {
                console.log('Response data:', j);
                if (j.ok) {
                    localStorage.setItem('eject_token', token);
                    $who.textContent = j.user;
                    showApp();
                    touch();
                    loadFiles();
                } else {
                    alert('Ссылка устарела или недействительна. Получите новую ссылку через /link в боте.');
                    showLogin();
                }
            }).catch(e => {
            console.error('Validation error:', e);
            alert('Ошибка соединения: ' + e.message);
            showLogin();
        });
    }

    function touch() {
        lastActivity = Date.now();
        fetch(`/auth/validate?token=${encodeURIComponent(token)}`).catch(()=>{});
    }

    // Таймер неактивности
    setInterval(() => {
        const remaining = Math.max(0, inactivitySeconds - Math.floor((Date.now()-lastActivity)/1000));
        const m = Math.floor(remaining/60), s = remaining%60;
        $timer.textContent = `${m}:${s.toString().padStart(2,'0')}`;
        if (remaining <= 0) {
            logout();
        }
    }, 1000);

    // Отслеживание активности
    ['mousemove','keydown','click','touchstart'].forEach(evt => {
        window.addEventListener(evt, () => {
            lastActivity = Date.now();
            fetch(`/auth/validate?token=${encodeURIComponent(token)}`).catch(()=>{});
        });
    });

    function loadFiles() {
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
        .catch(e => alert('Ошибка загрузки: ' + e.message));
    }

    function buildFileTree() {
        $fileTree.innerHTML = '';
        
        // Корневая папка
        const rootItem = document.createElement('div');
        rootItem.className = 'tree-item root' + (!isInTrash && currentPath === '' ? ' selected' : '');
        rootItem.textContent = 'Корень';
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
            
            renderTreeLevel(trashTree, $fileTree, 1, true);
        }
    }
    
    function renderTreeLevel(level, container, depth, inTrash = false) {
        Object.keys(level).sort().forEach(name => {
            const node = level[name];
            const item = document.createElement('div');
            const isSelected = inTrash ? (isInTrash && currentPath === node.path) : (!isInTrash && currentPath === node.path);
            item.className = 'tree-item folder' + (isSelected ? ' selected' : '');
            item.style.paddingLeft = (10 + depth * 20) + 'px';
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
        buildFileTree();
        showFilesInPath(path);
    }
    
    function selectTrash() {
        isInTrash = true;
        currentPath = '';
        buildFileTree();
        showTrash();
    }
    
    function selectTrashPath(path) {
        isInTrash = true;
        currentPath = path;
        buildFileTree();
        showTrash();
    }

    function showFilesInPath(path) {
        $currentPath.textContent = path || 'Корень';
        
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
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ' + folderName;
            cell.onclick = () => selectPath(folder);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            
            const actionsCell = row.insertCell();
            const deleteBtn = document.createElement('button');
            deleteBtn.textContent = 'Удалить';
            deleteBtn.onclick = (e) => {
                e.stopPropagation();
                deleteFolder(folder);
            };
            actionsCell.appendChild(deleteBtn);
        });
        
        // Файлы
        filesInPath.forEach(file => {
            const row = $filesTable.insertRow();
            const fileName = file.id.includes('/') ? file.id.split('/').pop() : file.id;
            const fileSize = formatFileSize(file.size);
            const fileDate = new Date(file.uploadedAt).toLocaleString();
            
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
                <button onclick="downloadFile('${file.id}')">Скачать</button>
                <button onclick="shareFile('${file.id}')">Поделиться</button>
                <button onclick="deleteFile('${file.id}')">Удалить</button>
            `;
        });
    }

    function formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    window.uploadFiles = function() {
        $fileInput.click();
    };

    $fileInput.addEventListener('change', function() {
        const files = this.files;
        if (files.length === 0) return;

        // Проверяем квоту перед загрузкой
        fetch(`/api/files/quota?token=${encodeURIComponent(token)}`)
            .then(r => {
                if (!r.ok) throw new Error('Ошибка получения информации о квоте');
                return r.json();
            })
            .then(quota => {
                let totalSize = 0;
                for (let file of files) {
                    totalSize += file.size;
                }
                
                if (quota.remaining < totalSize) {
                    const remainingMB = (quota.remaining / 1024 / 1024).toFixed(2);
                    const neededMB = (totalSize / 1024 / 1024).toFixed(2);
                    alert(`Недостаточно места!\nОсталось: ${remainingMB} MB\nНужно: ${neededMB} MB`);
                    this.value = ''; // Очищаем input
                    return;
                }
                
                // Загружаем файлы последовательно
                uploadFilesSequentially(Array.from(files), 0);
            })
            .catch(e => {
                alert('Ошибка проверки квоты: ' + e.message);
                this.value = ''; // Очищаем input
            });
    });
    
    function uploadFilesSequentially(files, index) {
        if (index >= files.length) {
            $fileInput.value = ''; // Очищаем input после всех загрузок
            hideUploadProgress();
            loadFiles(); // Обновляем список файлов
            return;
        }
        
        const file = files[index];
        showUploadProgress(file.name, index + 1, files.length);
        
        const fd = new FormData();
        fd.append('file', file);
        fd.append('token', token);
        if (currentPath) fd.append('path', currentPath);

        const xhr = new XMLHttpRequest();
        
        xhr.upload.addEventListener('progress', (e) => {
            if (e.lengthComputable) {
                const percentComplete = (e.loaded / e.total) * 100;
                updateUploadProgress(percentComplete);
            }
        });
        
        xhr.onload = function() {
            if (xhr.status === 200) {
                try {
                    const response = JSON.parse(xhr.responseText);
                    if (response.renamed) {
                        alert(`Файл "${response.originalName}" был переименован в "${response.newName}" (файл с таким именем уже существовал)`);
                    }
                } catch (e) {
                    // Игнорируем ошибки парсинга
                }
                // Загружаем следующий файл
                uploadFilesSequentially(files, index + 1);
            } else {
                alert(`Ошибка загрузки ${file.name}: ${xhr.responseText}`);
                // Продолжаем загрузку остальных файлов
                uploadFilesSequentially(files, index + 1);
            }
        };
        
        xhr.onerror = function() {
            alert(`Ошибка загрузки ${file.name}: Соединение прервано`);
            // Продолжаем загрузку остальных файлов
            uploadFilesSequentially(files, index + 1);
        };
        
        xhr.ontimeout = function() {
            alert(`Ошибка загрузки ${file.name}: Превышено время ожидания`);
            // Продолжаем загрузку остальных файлов
            uploadFilesSequentially(files, index + 1);
        };
        
        xhr.timeout = 300000; // 5 минут
        xhr.open('POST', '/api/files/upload');
        xhr.send(fd);
    }
    
    function showUploadProgress(fileName, current, total) {
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
                min-width: 300px;
            `;
            document.body.appendChild(progressDiv);
        }
        
        progressDiv.innerHTML = `
            <div style="margin-bottom: 10px; font-weight: bold;">Загрузка файлов (${current}/${total})</div>
            <div style="margin-bottom: 5px; font-size: 14px;">${fileName}</div>
            <div style="background: #f0f0f0; border-radius: 3px; overflow: hidden;">
                <div id="uploadProgressBar" style="background: #007acc; height: 20px; width: 0%; transition: width 0.3s;"></div>
            </div>
            <div id="uploadProgressText" style="text-align: center; margin-top: 5px; font-size: 12px;">0%</div>
        `;
    }
    
    function updateUploadProgress(percent) {
        const progressBar = document.getElementById('uploadProgressBar');
        const progressText = document.getElementById('uploadProgressText');
        if (progressBar && progressText) {
            progressBar.style.width = percent + '%';
            progressText.textContent = Math.round(percent) + '%';
        }
    }
    
    function hideUploadProgress() {
        const progressDiv = document.getElementById('uploadProgress');
        if (progressDiv) {
            progressDiv.remove();
        }
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

    window.createFolder = function() {
        const folderName = prompt('Введите имя папки:');
        if (!folderName) return;
        
        // Валидация имени папки
        if (/[<>:"/\\|?*]/.test(folderName)) {
            alert('Имя папки содержит запрещенные символы: < > : " / \\ | ? *');
            return;
        }

        const folderPath = currentPath ? `${currentPath}/${folderName}` : folderName;
        
        fetch(`/api/files/mkdir?token=${encodeURIComponent(token)}&path=${encodeURIComponent(folderPath)}`, {
            method: 'POST'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка создания папки');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };
    
    window.deleteFolder = function(folderPath) {
        const folderName = folderPath.split('/').pop();
        if (!confirm(`Переместить папку "${folderName}" в корзину?`)) return;
        
        fetch(`/api/files/folder?path=${encodeURIComponent(folderPath)}&token=${encodeURIComponent(token)}`, {
            method: 'DELETE'
        })
        .then(r => {
            if (!r.ok) return r.text().then(t => { throw new Error(t); });
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };

    window.downloadFile = function(fileId) {
        window.open(`/api/files/download/${encodeURIComponent(fileId)}?token=${encodeURIComponent(token)}`);
    };

    window.shareFile = function(fileId) {
        fetch(`/api/files/share/${encodeURIComponent(fileId)}?token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(r => r.json())
        .then(data => {
            const shareUrl = window.location.origin + data.shareUrl;
            navigator.clipboard.writeText(shareUrl).then(() => {
                alert('Ссылка скопирована в буфер обмена: ' + shareUrl);
                loadFiles(); // Обновляем список для отображения ссылки
            }).catch(() => {
                prompt('Ссылка для скачивания:', shareUrl);
                loadFiles();
            });
        })
        .catch(e => alert('Ошибка создания ссылки: ' + e.message));
    };
    
    window.copyExistingShare = function(fileId) {
        fetch(`/api/files/share/${encodeURIComponent(fileId)}?token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(r => r.json())
        .then(data => {
            const shareUrl = window.location.origin + data.shareUrl;
            navigator.clipboard.writeText(shareUrl).then(() => {
                alert('Ссылка скопирована в буфер обмена: ' + shareUrl);
            }).catch(() => {
                prompt('Ссылка для скачивания:', shareUrl);
            });
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };
    
    window.deleteShareLink = function(fileId) {
        if (!confirm('Удалить ссылку на файл?')) return;
        
        fetch(`/api/files/share/${encodeURIComponent(fileId)}?token=${encodeURIComponent(token)}`, {
            method: 'DELETE'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка удаления ссылки');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };

    window.deleteFile = function(fileId) {
        const fileName = fileId.split('/').pop();
        if (!confirm(`Переместить файл "${fileName}" в корзину?`)) return;
        
        fetch(`/api/files/delete?id=${encodeURIComponent(fileId)}&token=${encodeURIComponent(token)}`, {
            method: 'DELETE'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка удаления');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };

    function logout() {
        localStorage.removeItem('eject_token');
        token = null;
        showLogin();
        alert('Сессия завершена. Получите новую ссылку через /link в боте.');
    }

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
                <td colspan="4" style="text-align: center; font-weight: bold;">
                    В корзине ${trashFiles.length} элементов
                </td>
                <td>
                    <button onclick="clearTrash()" style="background: #dc3545; color: white;">Очистить все</button>
                </td>
            `;
        }
        
        // Кнопка "Назад" (если не в корне корзины)
        if (currentPath !== '') {
            const row = $filesTable.insertRow();
            const parentPath = currentPath.includes('/') ? currentPath.substring(0, currentPath.lastIndexOf('/')) : '';
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
            const cell = row.insertCell();
            cell.style.cursor = 'pointer';
            cell.style.color = '#007acc';
            cell.textContent = '📁 ' + folderName;
            cell.onclick = () => selectTrashPath(folder);
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            row.insertCell().textContent = '-';
            
            const actionsCell = row.insertCell();
            const restoreBtn = document.createElement('button');
            restoreBtn.textContent = 'Восстановить';
            restoreBtn.style.background = '#28a745';
            restoreBtn.style.color = 'white';
            restoreBtn.style.marginRight = '5px';
            restoreBtn.onclick = (e) => {
                e.stopPropagation();
                restoreFromTrash(folder);
            };
            
            const deleteBtn = document.createElement('button');
            deleteBtn.textContent = 'Удалить навсегда';
            deleteBtn.style.background = '#dc3545';
            deleteBtn.style.color = 'white';
            deleteBtn.onclick = (e) => {
                e.stopPropagation();
                deleteFromTrash(folder);
            };
            
            actionsCell.appendChild(restoreBtn);
            actionsCell.appendChild(deleteBtn);
        });
        
        // Файлы
        itemsInPath.forEach(item => {
            const row = $filesTable.insertRow();
            const itemName = item.id.includes('/') ? item.id.split('/').pop() : item.id;
            const isFolder = item.size === -1;
            const itemSize = isFolder ? '-' : formatFileSize(item.size);
            const itemDate = new Date(item.uploadedAt).toLocaleString();
            const icon = isFolder ? '📁' : '📄';
            
            row.innerHTML = `
                <td>${icon} ${itemName}</td>
                <td>${itemSize}</td>
                <td>${itemDate}</td>
                <td>-</td>
                <td>
                    <button onclick="restoreFromTrash('${item.id}')" style="background: #28a745; color: white; margin-right: 5px;">Восстановить</button>
                    <button onclick="deleteFromTrash('${item.id}')" style="background: #dc3545; color: white;">Удалить навсегда</button>
                </td>
            `;
        });
        
        if (itemsInPath.length === 0 && subfolders.length === 0 && currentPath === '') {
            const row = $filesTable.insertRow();
            row.innerHTML = `
                <td colspan="5" style="text-align: center; color: #666; font-style: italic;">
                    Корзина пуста
                </td>
            `;
        }
    }
    
    window.clearTrash = function() {
        if (!confirm('Очистить корзину? Все файлы будут удалены навсегда!')) return;
        
        fetch(`/api/files/trash/clear?token=${encodeURIComponent(token)}`, {
            method: 'DELETE'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка очистки корзины');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };
    
    window.deleteFromTrash = function(itemId) {
        if (!confirm('Удалить навсегда? Это действие нельзя отменить!')) return;
        
        fetch(`/api/files/trash/${encodeURIComponent(itemId)}?token=${encodeURIComponent(token)}`, {
            method: 'DELETE'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка удаления');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
    };
    
    window.restoreFromTrash = function(itemId) {
        const itemName = itemId.split('/').pop();
        if (!confirm(`Восстановить "${itemName}"?`)) return;
        
        fetch(`/api/files/trash/restore/${encodeURIComponent(itemId)}?token=${encodeURIComponent(token)}`, {
            method: 'POST'
        })
        .then(r => {
            if (!r.ok) throw new Error('Ошибка восстановления');
            loadFiles();
        })
        .catch(e => alert('Ошибка: ' + e.message));
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
            $quotaProgress.style.background = '#28a745';
        } else if (percentage < 90) {
            $quotaProgress.style.background = '#ffc107';
        } else {
            $quotaProgress.style.background = '#dc3545';
        }
    }

    // Автоматическая проверка токена
    if (token) {
        doValidate();
    } else {
        showLogin();
    }
})();