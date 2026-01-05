-- Risk Request Audit Table
CREATE TABLE IF NOT EXISTS risk_request_audit (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(100) NOT NULL UNIQUE,
    event_id VARCHAR(100) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    fraud_score DECIMAL(5, 4) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    reason TEXT,
    model_version VARCHAR(50),
    processing_time_ms BIGINT,
    cached BOOLEAN DEFAULT FALSE,
    request_body JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_event_id ON risk_request_audit(event_id);
CREATE INDEX idx_audit_subject ON risk_request_audit(subject_type, subject_id);
CREATE INDEX idx_audit_decision ON risk_request_audit(decision);
CREATE INDEX idx_audit_created_at ON risk_request_audit(created_at);

-- Risk Threshold Configuration Table
CREATE TABLE IF NOT EXISTS risk_threshold_config (
    id BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(50) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    reject_threshold DECIMAL(5, 4) NOT NULL DEFAULT 0.8,
    review_threshold DECIMAL(5, 4) NOT NULL DEFAULT 0.5,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(subject_type, model_version)
);

CREATE INDEX idx_threshold_subject_type ON risk_threshold_config(subject_type);
CREATE INDEX idx_threshold_active ON risk_threshold_config(is_active);

-- Feature Definition Table
CREATE TABLE IF NOT EXISTS feature_definition (
    id BIGSERIAL PRIMARY KEY,
    feature_name VARCHAR(100) NOT NULL UNIQUE,
    feature_type VARCHAR(50) NOT NULL, -- numeric, categorical, boolean
    description TEXT,
    default_value VARCHAR(200),
    feature_version VARCHAR(50) DEFAULT 'v1',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feature_name ON feature_definition(feature_name);
CREATE INDEX idx_feature_version ON feature_definition(feature_version);
CREATE INDEX idx_feature_active ON feature_definition(is_active);

-- Insert default threshold configurations
INSERT INTO risk_threshold_config (subject_type, model_version, reject_threshold, review_threshold, description)
VALUES
    ('user', 'v1', 0.8, 0.5, 'Default thresholds for user risk assessment'),
    ('device', 'v1', 0.75, 0.45, 'Default thresholds for device risk assessment'),
    ('merchant', 'v1', 0.85, 0.55, 'Default thresholds for merchant risk assessment')
ON CONFLICT (subject_type, model_version) DO NOTHING;

-- Insert sample feature definitions
INSERT INTO feature_definition (feature_name, feature_type, description, feature_version)
VALUES
    ('transaction_count_24h', 'numeric', 'Number of transactions in last 24 hours', 'v1'),
    ('avg_transaction_amount_7d', 'numeric', 'Average transaction amount in last 7 days', 'v1'),
    ('account_age_days', 'numeric', 'Age of account in days', 'v1'),
    ('is_new_device', 'boolean', 'Whether the device is new', 'v1'),
    ('risk_score_30d', 'numeric', 'Risk score in last 30 days', 'v1')
ON CONFLICT (feature_name) DO NOTHING;

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_risk_request_audit_updated_at BEFORE UPDATE ON risk_request_audit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_threshold_config_updated_at BEFORE UPDATE ON risk_threshold_config
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_feature_definition_updated_at BEFORE UPDATE ON feature_definition
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
