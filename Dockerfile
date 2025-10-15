# Use OpenJDK 21 and install Maven
FROM openjdk:21-jdk-slim

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the entire project
COPY . .

# Build the project
RUN mvn clean package -DskipTests=true

# The built JAR will be in target/ directory
# You can copy it out or use a multi-stage build if needed
