# Docker Cheatsheet

## Containers

List running containers:

```powershell
docker ps
```

List all containers:

```powershell
docker ps -a
```

Start a container:

```powershell
docker start <container_name>
```

Stop a container:

```powershell
docker stop <container_name>
```

Restart a container:

```powershell
docker restart <container_name>
```

Remove a container:

```powershell
docker rm <container_name>
```

Force remove a container:

```powershell
docker rm -f <container_name>
```

Show logs:

```powershell
docker logs <container_name>
```

Follow logs:

```powershell
docker logs -f <container_name>
```

Open a shell inside a container:

```powershell
docker exec -it <container_name> sh
```

Inspect a container:

```powershell
docker inspect <container_name>
```

## Images, Volumes, Networks

List images:

```powershell
docker images
```

List volumes:

```powershell
docker volume ls
```

List networks:

```powershell
docker network ls
```

## Compose

Start services:

```powershell
docker compose up
docker compose up -d
```

Start a specific service:

```powershell
docker compose up -d backend
```

Show compose services:

```powershell
docker compose ps
```

Show logs:

```powershell
docker compose logs
```

Follow logs:

```powershell
docker compose logs -f
docker compose logs -f frontend
```

Stop services:

```powershell
docker compose stop
docker compose stop frontend
```

Restart services:

```powershell
docker compose restart
docker compose restart backend
```

Shut down stack:

```powershell
docker compose down
```

Shut down and remove volumes:

```powershell
docker compose down -v
```

Rebuild and start:

```powershell
docker compose up -d --build
```

Force recreate:

```powershell
docker compose up -d --force-recreate
```

Use multiple compose files:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
```

## Cleanup

Remove stopped containers:

```powershell
docker container prune
```

Remove unused images:

```powershell
docker image prune -a
```

Remove unused volumes:

```powershell
docker volume prune
```

Remove unused Docker resources:

```powershell
docker system prune -a
```

Remove unused Docker resources including volumes:

```powershell
docker system prune -a --volumes
```

## Project Commands

Start the local stack:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
```

Start only local auth services:

```powershell
docker compose -f docker-compose.dev-auth.yml up -d
```

Stop the local stack:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

Stop local auth services:

```powershell
docker compose -f docker-compose.dev-auth.yml down
```

Follow frontend logs:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml logs -f frontend
```

Follow backend logs:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml logs -f backend
```

Follow Keycloak logs:

```powershell
docker compose -f docker-compose.dev-auth.yml logs -f keycloak
```
